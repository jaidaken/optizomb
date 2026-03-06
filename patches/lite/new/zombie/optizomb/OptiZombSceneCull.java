package zombie.optizomb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.debug.DebugLog;

/**
 * OptiZomb Scene Cull + Bone LOD optimization.
 *
 * Assigns LOD tiers to zombies based on distance rank in sceneCullZombies().
 * Distant zombies skip cosmetic bone transforms (fingers, toes, nubs).
 */
public final class OptiZombSceneCull {

    // Diagnostics
    private static long lastReportTime = 0;
    private static int lodTier0 = 0;
    private static int lodTier1 = 0;
    private static int lodTier2 = 0;
    private static int lodTier3 = 0;
    private static int boneSkipCount = 0;

    // Bone skip caches: SkinningData → boolean[] (true = skippable)
    private static final ConcurrentHashMap<SkinningData, boolean[]> boneSkipCacheLOD1 = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SkinningData, boolean[]> boneSkipCacheLOD2 = new ConcurrentHashMap<>();

    // --- LOD Assignment ---

    public static void assignLOD(IsoZombie zombie, int rank) {
        int interval;
        int boneLOD;
        if (rank <= 50) {
            interval = 1;
            boneLOD = 0;
            lodTier0++;
        } else if (rank <= 200) {
            interval = 2;
            boneLOD = 0;
            lodTier1++;
        } else if (rank <= 500) {
            interval = 4;
            boneLOD = (rank > 300) ? 2 : 1;
            lodTier2++;
        } else {
            interval = 8;
            boneLOD = 2;
            lodTier3++;
        }
        zombie.cullRank = rank;
        zombie.setLightUpdateInterval(interval);
        if (OptiZombConfig.BONE_LOD
                && zombie.legsSprite != null
                && zombie.legsSprite.modelSlot != null
                && zombie.legsSprite.modelSlot.model != null
                && zombie.legsSprite.modelSlot.model.AnimPlayer != null) {
            zombie.legsSprite.modelSlot.model.AnimPlayer.boneLOD = boneLOD;
        }
    }

    // --- Bone Skip Classification ---

    public static boolean isBoneSkippable(SkinningData data, int boneIdx, int lodLevel) {
        if (data == null) return false;
        ConcurrentHashMap<SkinningData, boolean[]> cache = (lodLevel >= 2) ? boneSkipCacheLOD2 : boneSkipCacheLOD1;
        boolean[] skipArray = cache.get(data);
        if (skipArray == null) {
            skipArray = buildSkipArray(data, lodLevel >= 2);
            cache.putIfAbsent(data, skipArray);
            skipArray = cache.get(data);
        }
        return boneIdx < skipArray.length && skipArray[boneIdx];
    }

    private static boolean[] buildSkipArray(SkinningData data, boolean aggressive) {
        int numBones = data.numBones();
        boolean[] skipArray = new boolean[numBones];
        for (int i = 0; i < numBones; i++) {
            SkinningBone bone = data.getBoneAt(i);
            if (bone == null || i == 0) {
                skipArray[i] = false;
                continue;
            }
            String name = bone.Name;
            if (name == null) {
                skipArray[i] = false;
                continue;
            }
            boolean isStructural;
            if (aggressive) {
                // LOD 2: core skeleton only (no hands, feet, clavicle, backpack)
                isStructural = name.equals("Bip01")
                    || name.contains("Pelvis") || name.contains("Spine")
                    || name.contains("Neck") || name.contains("Head")
                    || name.contains("Thigh") || name.contains("Calf")
                    || name.contains("UpperArm") || name.contains("Forearm")
                    || name.contains("Prop");
            } else {
                // LOD 1: structural bones (skip fingers, toes, nubs)
                isStructural = name.equals("Bip01")
                    || name.contains("Pelvis") || name.contains("Spine")
                    || name.contains("Neck") || name.contains("Head")
                    || name.contains("Thigh") || name.contains("Calf")
                    || name.contains("Foot") || name.contains("Clavicle")
                    || name.contains("UpperArm") || name.contains("Forearm")
                    || name.contains("Hand") || name.contains("Prop")
                    || name.contains("BackPack");
            }
            skipArray[i] = !isStructural;
        }
        return skipArray;
    }

    // --- Cached Cull Score Sort (Opt 34) ---

    private static final Comparator<IsoZombie> CACHED_SCORE_COMPARATOR = (a, b) -> {
        if (a.cachedCullScore < b.cachedCullScore) return 1;
        if (a.cachedCullScore > b.cachedCullScore) return -1;
        return 0;
    };

    private static float computeCullScore(IsoZombie zombie) {
        float best = Float.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            IsoPlayer player = IsoPlayer.players[i];
            if (player != null && player.getCurrentSquare() != null) {
                float score = player.getZombieRelevenceScore(zombie);
                if (score > best) best = score;
            }
        }
        return best;
    }

    public static void sortByCachedScore(ArrayList<IsoZombie> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            IsoZombie z = list.get(i);
            z.cachedCullScore = computeCullScore(z);
        }
        Collections.sort(list, CACHED_SCORE_COMPARATOR);
    }

    // --- Diagnostic counters ---

    public static void recordBoneSkip() {
        boneSkipCount++;
    }

    // --- Diagnostics ---

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.SCENE_CULL) return;
        if (lodTier0 == 0 && lodTier1 == 0 && lodTier2 == 0 && lodTier3 == 0) return;

        DebugLog.General.println("[OptiZomb] SCENE_CULL (5.0s):"
            + " t0(1-50)=" + lodTier0
            + " t1(51-200)=" + lodTier1
            + " t2(201-500)=" + lodTier2
            + " t3(501+,int8)=" + lodTier3
            + " | boneSkips=" + boneSkipCount);

        lodTier0 = 0;
        lodTier1 = 0;
        lodTier2 = 0;
        lodTier3 = 0;
        boneSkipCount = 0;
    }

    private OptiZombSceneCull() {}
}

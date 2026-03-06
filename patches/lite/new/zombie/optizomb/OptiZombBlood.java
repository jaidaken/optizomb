package zombie.optizomb;

import zombie.core.SpriteRenderer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.debug.DebugLog;
import zombie.iso.IsoCamera;
import zombie.iso.IsoChunk;
import zombie.iso.IsoFloorBloodSplat;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoDirections;
import zombie.iso.IsoUtils;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;
import java.util.ArrayList;

/**
 * OptiZomb Blood rendering optimization (Opt 27d).
 *
 * Four optimization phases:
 *   A) Early offscreen culling + fade state mutation before offscreen check
 *   B) Single-entry tile light cache (persists across type buckets)
 *   C) Cached per-splat color hash (NaN sentinel on IsoFloorBloodSplat fields)
 *   D) Extract texture once per type, direct SpriteRenderer.render() call
 */
public final class OptiZombBlood {

    // Diagnostics
    private static long lastReportTime = 0;
    private static int splatsRendered = 0;
    private static int splatsOffscreenCulled = 0;
    private static int tileLightCacheHits = 0;
    private static int tileLightCacheMisses = 0;
    private static int colorCacheHits = 0;
    private static int colorCacheMisses = 0;

    // Phase B: Single-entry tile light cache state (reset per renderBloodForChunks call)
    private static IsoChunk cachedChunk;
    private static int cachedSqX = Integer.MIN_VALUE;
    private static int cachedSqY = Integer.MIN_VALUE;
    private static float cachedLightR = 1.0f;
    private static float cachedLightG = 1.0f;
    private static float cachedLightB = 1.0f;
    private static boolean cachedHasSquare = false;

    /**
     * Render all blood splats of all types using the optimized path.
     * Called from IsoChunkMap.renderBloodForChunks after splatByType is populated.
     *
     * @param splatByType  per-type splat lists (already populated by vanilla collection loop)
     * @param inf          shared ColorInfo instance (reused per splat)
     * @param float0       current world age hours
     * @param playerIndex  current player index for vert light lookups
     */
    public static void renderOptimized(
            ArrayList<ArrayList<IsoFloorBloodSplat>> splatByType,
            ColorInfo inf,
            float float0,
            int playerIndex) {

        // Reset tile light cache for this frame
        cachedChunk = null;
        cachedSqX = Integer.MIN_VALUE;
        cachedSqY = Integer.MIN_VALUE;

        for (int typeIdx = 0; typeIdx < splatByType.size(); typeIdx++) {
            ArrayList<IsoFloorBloodSplat> arrayList = splatByType.get(typeIdx);
            if (arrayList.isEmpty()) continue;

            String string = IsoFloorBloodSplat.FloorBloodTypes[typeIdx];
            IsoSprite sprite0 = IsoFloorBloodSplat.SpriteMap.get(string);
            if (sprite0 == null) {
                sprite0 = IsoSprite.CreateSprite(IsoSpriteManager.instance);
                sprite0.LoadFramesPageSimple(string, string, string, string);
                IsoFloorBloodSplat.SpriteMap.put(string, sprite0);
            }

            // Phase D: Extract texture once per type
            Texture bloodTex = sprite0.CurrentAnim.Frames.get(0).getTexture(IsoDirections.N);
            if (bloodTex == null) { arrayList.clear(); continue; }
            float texOffX = bloodTex.offsetX;
            float texOffY = bloodTex.offsetY;
            float texW = bloodTex.getWidth();
            float texH = bloodTex.getHeight();

            for (int i = 0; i < arrayList.size(); i++) {
                IsoFloorBloodSplat splat = arrayList.get(i);

                // Phase A: Handle fade state mutation BEFORE offscreen check
                int preFade = splat.fade;
                if (preFade > 0) {
                    if (--splat.fade == 0) {
                        splat.chunk.FloorBloodSplatsFade.remove(splat);
                    }
                }

                // Phase A: Early offscreen culling
                float worldX = splat.chunk.wx * 10 + splat.x;
                float worldY = splat.chunk.wy * 10 + splat.y;
                float screenX = IsoUtils.XToScreen(worldX, worldY, splat.z, 0);
                float screenY = IsoUtils.YToScreen(worldX, worldY, splat.z, 0);
                screenX = (int)screenX + IsoSprite.globalOffsetX;
                screenY = (int)screenY + IsoSprite.globalOffsetY;
                if (screenX >= IsoCamera.frameState.OffscreenWidth || screenX + 64.0f <= 0.0f
                    || screenY >= IsoCamera.frameState.OffscreenHeight || screenY + 64.0f <= 0.0f) {
                    splatsOffscreenCulled++;
                    continue;
                }

                // Phase C: Cached per-splat color hash (immutable: depends only on x, y, Type)
                if (Float.isNaN(splat.cachedR)) {
                    float float1 = (splat.x + splat.y / splat.x) * (splat.Type + 1);
                    float float2 = float1 * splat.x / splat.y * (splat.Type + 1) / (float1 + splat.y);
                    float float3 = float2 * float1 * float2 * splat.x / (splat.y + 2.0f);
                    float1 *= 42367.543f;
                    float2 *= 6367.123f;
                    float3 *= 23367.133f;
                    float1 %= 1000.0f;
                    float2 %= 1000.0f;
                    float3 %= 1000.0f;
                    float1 /= 1000.0f;
                    float2 /= 1000.0f;
                    float3 /= 1000.0f;
                    if (float1 > 0.25f) {
                        float1 = 0.25f;
                    }
                    splat.cachedR = 1.0f - float1 * 2.0f + float2 / 3.0f;
                    splat.cachedG = 1.0f - float1 * 2.0f - float3 / 3.0f;
                    splat.cachedB = 1.0f - float1 * 2.0f - float3 / 3.0f;
                    colorCacheMisses++;
                } else {
                    colorCacheHits++;
                }

                inf.r = splat.cachedR;
                inf.g = splat.cachedG;
                inf.b = splat.cachedB;
                inf.a = 0.27f;

                // Age-based darkening
                float float4 = float0 - splat.worldAge;
                if (float4 >= 0.0f && float4 < 72.0f) {
                    float float5 = 1.0f - float4 / 72.0f;
                    inf.r *= 0.2f + float5 * 0.8f;
                    inf.g *= 0.2f + float5 * 0.8f;
                    inf.b *= 0.2f + float5 * 0.8f;
                    inf.a *= 0.25f + float5 * 0.75f;
                } else {
                    inf.r *= 0.2f;
                    inf.g *= 0.2f;
                    inf.b *= 0.2f;
                    inf.a *= 0.25f;
                }

                // Fade alpha (using pre-decrement value for visual consistency)
                if (preFade > 0) {
                    inf.a = inf.a * (preFade / (PerformanceSettings.getLockFPS() * 5.0f));
                }

                // Phase B: Cached tile light lookup
                IsoChunk splatChunk = splat.chunk;
                int sqX = (int)splat.x;
                int sqY = (int)splat.y;
                if (splatChunk != cachedChunk || sqX != cachedSqX || sqY != cachedSqY) {
                    cachedChunk = splatChunk;
                    cachedSqX = sqX;
                    cachedSqY = sqY;
                    IsoGridSquare square = splatChunk.getGridSquare(sqX, sqY, (int)splat.z);
                    if (square != null) {
                        cachedHasSquare = true;
                        int v0 = square.getVertLight(0, playerIndex);
                        int v1 = square.getVertLight(1, playerIndex);
                        int v2 = square.getVertLight(2, playerIndex);
                        int v3 = square.getVertLight(3, playerIndex);
                        int sumR = (v0 & 0xFF) + (v1 & 0xFF) + (v2 & 0xFF) + (v3 & 0xFF);
                        int sumG = ((v0>>8)&0xFF) + ((v1>>8)&0xFF) + ((v2>>8)&0xFF) + ((v3>>8)&0xFF);
                        int sumB = ((v0>>16)&0xFF) + ((v1>>16)&0xFF) + ((v2>>16)&0xFF) + ((v3>>16)&0xFF);
                        cachedLightR = sumR * 0.000980392f;
                        cachedLightG = sumG * 0.000980392f;
                        cachedLightB = sumB * 0.000980392f;
                    } else {
                        cachedHasSquare = false;
                    }
                    tileLightCacheMisses++;
                } else {
                    tileLightCacheHits++;
                }
                if (cachedHasSquare) {
                    inf.r *= cachedLightR;
                    inf.g *= cachedLightG;
                    inf.b *= cachedLightB;
                }

                // Phase D: Direct SpriteRenderer call — bypasses redundant
                // XToScreen/YToScreen, offscreen check, and 3 method dispatches
                SpriteRenderer.instance.render(
                    bloodTex,
                    screenX + texOffX, screenY + texOffY,
                    texW, texH,
                    inf.r, inf.g, inf.b, inf.a, null
                );
                splatsRendered++;
            }
        }
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.BLOOD) return;
        if (splatsRendered == 0 && splatsOffscreenCulled == 0) return;

        DebugLog.General.println("[OptiZomb] BLOOD: rendered=" + splatsRendered
            + " offscreenCulled=" + splatsOffscreenCulled
            + " tileLightCache=" + tileLightCacheHits + "hit/" + tileLightCacheMisses + "miss"
            + " colorCache=" + colorCacheHits + "hit/" + colorCacheMisses + "miss");

        splatsRendered = 0;
        splatsOffscreenCulled = 0;
        tileLightCacheHits = 0;
        tileLightCacheMisses = 0;
        colorCacheHits = 0;
        colorCacheMisses = 0;
    }

    private OptiZombBlood() {}
}

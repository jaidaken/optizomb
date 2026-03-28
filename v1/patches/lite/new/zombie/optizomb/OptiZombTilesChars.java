package zombie.optizomb;

import zombie.debug.DebugLog;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;

public final class OptiZombTilesChars {

    private static boolean confirmed = false;

    // Diagnostic counters
    private static int occlusionCacheHits = 0;
    private static int occlusionCacheMisses = 0;
    private static int renderFlagComputes = 0;
    private static int renderFlagCacheHits = 0;
    private static long lastReportTime = 0;

    public static short computeRenderFlags(IsoObject obj) {
        renderFlagComputes++;
        if (!confirmed) {
            confirmed = true;
            DebugLog.General.println("[OptiZomb] TILES_CHARS active: renderFlags bitmask + occlusion caching enabled");
        }
        short flags = 0;
        IsoSprite sprite = obj.sprite;
        if (sprite != null) {
            if (sprite.cutW) flags |= IsoObject.RF_CUT_W;
            if (sprite.cutN) flags |= IsoObject.RF_CUT_N;
            if (sprite.solidfloor || sprite.renderLayer == 1) flags |= IsoObject.RF_SOLID_FLOOR_OR_LAYER1;
            if (sprite.isBush || sprite.canBeRemoved || sprite.attachedFloor) flags |= IsoObject.RF_BUSH_OR_REMOVABLE;
            if (sprite.alwaysDraw) flags |= IsoObject.RF_ALWAYS_DRAW;
            PropertyContainer props = sprite.getProperties();
            if (props != null) {
                if (props.Is(IsoFlagType.attachedW)) flags |= IsoObject.RF_ATTACHED_W;
                if (props.Is(IsoFlagType.attachedN)) flags |= IsoObject.RF_ATTACHED_N;
            }
        }
        if (obj instanceof IsoWindow) flags |= IsoObject.RF_IS_WINDOW;
        if (obj instanceof IsoDoor) flags |= IsoObject.RF_IS_DOOR;
        if (obj instanceof IsoThumpable) flags |= IsoObject.RF_IS_THUMPABLE;
        return flags;
    }

    public static void recordOcclusionCacheHit() {
        occlusionCacheHits++;
    }

    public static void recordOcclusionCacheMiss() {
        occlusionCacheMisses++;
    }

    public static void recordRenderFlagCacheHit() {
        renderFlagCacheHits++;
    }

    public static void reportIfNeeded() {
        if (!OptiZombConfig.TILES_CHARS || !OptiZombConfig.DIAGNOSTICS) return;
        long now = System.nanoTime();
        if (lastReportTime == 0) {
            lastReportTime = now;
            return;
        }
        long elapsed = now - lastReportTime;
        if (elapsed >= 5_000_000_000L) {
            double secs = elapsed / 1_000_000_000.0;
            int totalOcc = occlusionCacheHits + occlusionCacheMisses;
            int totalRF = renderFlagComputes + renderFlagCacheHits;
            float occHitRate = totalOcc > 0 ? (100.0f * occlusionCacheHits / totalOcc) : 0.0f;
            float rfHitRate = totalRF > 0 ? (100.0f * renderFlagCacheHits / totalRF) : 0.0f;
            DebugLog.General.println(String.format(
                "[OptiZomb] TILES_CHARS (%.1fs): occCache %d/%d (%.0f%% hit), renderFlags %d computed / %d cached (%.0f%% hit)",
                secs, occlusionCacheHits, totalOcc, occHitRate, renderFlagComputes, renderFlagCacheHits, rfHitRate));
            occlusionCacheHits = 0;
            occlusionCacheMisses = 0;
            renderFlagComputes = 0;
            renderFlagCacheHits = 0;
            lastReportTime = now;
        }
    }

    private OptiZombTilesChars() {}
}

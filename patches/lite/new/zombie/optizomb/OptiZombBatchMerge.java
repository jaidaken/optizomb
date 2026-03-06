package zombie.optizomb;

import zombie.core.textures.Texture;
import zombie.core.textures.TextureID;
import zombie.debug.DebugLog;

public final class OptiZombBatchMerge {

    private static boolean confirmed = false;

    private static int mergesSaved = 0;
    private static int totalChecks = 0;
    private static long lastReportTime = 0;

    public static boolean isSameGLTexture(Texture a, Texture b) {
        totalChecks++;
        if (a == b) return true;
        if (a == null || b == null) return false;
        TextureID idA = a.getTextureId();
        TextureID idB = b.getTextureId();
        if (idA != null && idA == idB) {
            mergesSaved++;
            if (!confirmed) {
                confirmed = true;
                DebugLog.General.println("[OptiZomb] BATCH_MERGE active: GL texture ID comparison enabled");
            }
            return true;
        }
        return false;
    }

    public static void reportIfNeeded() {
        if (!OptiZombConfig.BATCH_MERGE || !OptiZombConfig.DIAGNOSTICS) return;
        long now = System.nanoTime();
        if (lastReportTime == 0) {
            lastReportTime = now;
            return;
        }
        long elapsed = now - lastReportTime;
        if (elapsed >= 5_000_000_000L) {
            double secs = elapsed / 1_000_000_000.0;
            float mergeRate = totalChecks > 0 ? (100.0f * mergesSaved / totalChecks) : 0.0f;
            DebugLog.General.println(String.format(
                "[OptiZomb] BATCH_MERGE (%.1fs): %d/%d texture checks merged (%.1f%% saved)",
                secs, mergesSaved, totalChecks, mergeRate));
            mergesSaved = 0;
            totalChecks = 0;
            lastReportTime = now;
        }
    }

    private OptiZombBatchMerge() {}
}

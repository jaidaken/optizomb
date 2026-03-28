package zombie.optizomb;

import zombie.debug.DebugLog;

/**
 * OptiZomb Floor FBO Cache diagnostics (Opt 21).
 *
 * Tracks dirty vs clean frames and FBO invalidations.
 * On clean frames, cached FBO is blit instead of re-rendering all floor tiles.
 */
public final class OptiZombFloorFBO {

    private static long lastReportTime = 0;
    private static int dirtyFrames = 0;
    private static int cleanFrames = 0;
    private static int invalidations = 0;

    public static void recordDirtyFrame() {
        dirtyFrames++;
    }

    public static void recordCleanFrame() {
        cleanFrames++;
    }

    public static void recordInvalidation() {
        invalidations++;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.FLOOR_FBO) return;
        if (dirtyFrames == 0 && cleanFrames == 0) return;

        int total = dirtyFrames + cleanFrames;
        int pct = total > 0 ? (cleanFrames * 100 / total) : 0;
        DebugLog.General.println("[OptiZomb] FLOOR_FBO (5.0s): dirtyFrames=" + dirtyFrames
            + " cleanFrames=" + cleanFrames
            + " (" + pct + "% cached)"
            + " | invalidations=" + invalidations
            + " | " + zombie.iso.FloorFBOCache.getDiagAndReset());

        dirtyFrames = 0;
        cleanFrames = 0;
        invalidations = 0;
    }

    private OptiZombFloorFBO() {}
}

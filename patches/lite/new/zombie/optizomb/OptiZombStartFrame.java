package zombie.optizomb;

import zombie.debug.DebugLog;

/**
 * OptiZomb StartFrame sub-phase profiling.
 *
 * Instruments Core.StartFrame() to identify which sub-operation
 * is responsible for the ~1.4ms per-frame cost.
 */
public final class OptiZombStartFrame {

    private static long offscreenBufNs = 0;
    private static long prePopNs = 0;
    private static long initCamNs = 0;
    private static long glCommandsNs = 0;
    private static int frameCount = 0;
    private static long lastReportTime = 0;

    public static void recordOffscreenBuf(long elapsedNs) {
        offscreenBufNs += elapsedNs;
    }

    public static void recordPrePop(long elapsedNs) {
        prePopNs += elapsedNs;
    }

    public static void recordInitCam(long elapsedNs) {
        initCamNs += elapsedNs;
    }

    public static void recordGlCommands(long elapsedNs) {
        glCommandsNs += elapsedNs;
    }

    public static void recordFrame() {
        frameCount++;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (frameCount == 0) return;

        float avgOffscreen = (offscreenBufNs / 1_000_000.0f) / frameCount;
        float avgPrePop = (prePopNs / 1_000_000.0f) / frameCount;
        float avgInitCam = (initCamNs / 1_000_000.0f) / frameCount;
        float avgGlCmds = (glCommandsNs / 1_000_000.0f) / frameCount;
        float total = avgOffscreen + avgPrePop + avgInitCam + avgGlCmds;

        DebugLog.General.println("[OptiZomb] STARTFRAME (5.0s): "
            + frameCount + " frames | offscreenBuf=" + String.format("%.2f", avgOffscreen)
            + "ms prePop=" + String.format("%.2f", avgPrePop)
            + "ms initCam=" + String.format("%.2f", avgInitCam)
            + "ms glCmds=" + String.format("%.2f", avgGlCmds)
            + "ms total=" + String.format("%.2f", total) + "ms");

        offscreenBufNs = 0;
        prePopNs = 0;
        initCamNs = 0;
        glCommandsNs = 0;
        frameCount = 0;
    }

    private OptiZombStartFrame() {}
}

package zombie.optizomb;

import zombie.debug.DebugLog;

/**
 * ZOOM optimization: defers dirtyRecalcGridStackTime to zoom completion.
 *
 * Vanilla sets dirtyRecalcGridStackTime = 2.0F every frame during zoom transitions,
 * forcing expensive grid stack recalculation ~30 times per zoom. This defers it to
 * the single frame when zoom reaches its target.
 */
public final class OptiZombZoom {

    /** Manual zoom speed multiplier (vanilla = 5.0F). */
    public static final float MANUAL_ZOOM_MULTIPLIER = 4.0F;

    private static long lastReportTime = System.currentTimeMillis();
    private static int zoomFrames;
    private static int gridRecalcDeferred;
    private static int gridRecalcTriggered;

    /**
     * Step zoom toward target (zooming in / value increasing).
     * @return true if zoom reached target (caller should trigger grid recalc)
     */
    public static boolean stepZoomUp(float[] zoom, float[] targetZoom, int playerIndex, float delta) {
        zoom[playerIndex] += delta;
        zoomFrames++;
        if (zoom[playerIndex] > targetZoom[playerIndex]
                || Math.abs(zoom[playerIndex] - targetZoom[playerIndex]) < 0.001F) {
            zoom[playerIndex] = targetZoom[playerIndex];
            gridRecalcTriggered++;
            return true;
        }
        gridRecalcDeferred++;
        return false;
    }

    /**
     * Step zoom toward target (zooming out / value decreasing).
     * @return true if zoom reached target (caller should trigger grid recalc)
     */
    public static boolean stepZoomDown(float[] zoom, float[] targetZoom, int playerIndex, float delta) {
        zoom[playerIndex] -= delta;
        zoomFrames++;
        if (zoom[playerIndex] < targetZoom[playerIndex]
                || Math.abs(zoom[playerIndex] - targetZoom[playerIndex]) < 0.001F) {
            zoom[playerIndex] = targetZoom[playerIndex];
            gridRecalcTriggered++;
            return true;
        }
        gridRecalcDeferred++;
        return false;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;
        if (!OptiZombConfig.ZOOM) return;
        if (zoomFrames == 0) return;
        DebugLog.General.println("[OptiZomb] ZOOM (5.0s): zoomFrames=" + zoomFrames
                + " gridRecalcDeferred=" + gridRecalcDeferred
                + " gridRecalcTriggered=" + gridRecalcTriggered);
        zoomFrames = 0;
        gridRecalcDeferred = 0;
        gridRecalcTriggered = 0;
    }

    private OptiZombZoom() {}
}

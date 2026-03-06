package zombie.optizomb;

import zombie.debug.DebugLog;

/**
 * OptiZomb Fog rendering diagnostics + optimization.
 *
 * Instruments ImprovedFog to measure per-row iteration cost
 * vs actual render cost, and count rows rendered/skipped.
 *
 * Distance-based row skip: rows more than SKIP_DISTANCE from the
 * player row render every 2nd row instead of every row. Saves
 * ~50% of ChunkMap lookups for distant fog tiles.
 */
public final class OptiZombFog {

    private static final int SKIP_DISTANCE = 15;

    private static int playerRow = 0;
    private static boolean skipActive = false;

    private static long lastReportTime = 0;
    private static int frameCount = 0;
    private static int rowsRendered = 0;
    private static int rowsSkipped = 0;
    private static int rowsDistSkipped = 0;
    private static int gridSquareLookups = 0;
    private static int gridSquaresSaved = 0;
    private static int segmentsRendered = 0;

    public static void startLayer(int pRow) {
        playerRow = pRow;
        skipActive = true;
    }

    /** Check if a row should be skipped based on distance from player. */
    public static boolean shouldSkipRow(int row) {
        if (!skipActive) return false;
        int dist = row - playerRow;
        if (dist < 0) dist = -dist;
        return dist > SKIP_DISTANCE && (row & 1) != 0;
    }

    /** Check if a rendered row should be doubled in height to cover the skipped neighbor. */
    public static boolean shouldDoubleHeight(int row) {
        if (!skipActive) return false;
        int dist = row - playerRow;
        if (dist < 0) dist = -dist;
        return dist > SKIP_DISTANCE && (row & 1) == 0;
    }

    public static void recordRowRendered() {
        rowsRendered++;
    }

    public static void recordRowSkipped() {
        rowsSkipped++;
    }

    public static void recordRowDistSkipped() {
        rowsDistSkipped++;
    }

    public static void recordGridSquareLookup() {
        gridSquareLookups++;
    }

    public static void recordGridSquareSaved() {
        gridSquaresSaved++;
    }

    public static void recordSegmentRendered() {
        segmentsRendered++;
    }

    public static void recordFrame() {
        frameCount++;
        skipActive = false;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (frameCount == 0) return;

        int totalRows = rowsRendered + rowsSkipped + rowsDistSkipped;
        int totalSkipped = rowsSkipped + rowsDistSkipped;
        int skipPct = totalRows > 0 ? (totalSkipped * 100 / totalRows) : 0;

        DebugLog.General.println("[OptiZomb] FOG (5.0s): "
            + frameCount + " frames"
            + " | rows=" + (totalRows / frameCount) + "/f"
            + " rendered=" + (rowsRendered / frameCount)
            + " qualSkip=" + (rowsSkipped / frameCount)
            + " distSkip=" + (rowsDistSkipped / frameCount)
            + " (" + skipPct + "% skip)"
            + " | gridLookups=" + (gridSquareLookups / frameCount)
            + " saved=" + (gridSquaresSaved / frameCount)
            + " segments=" + (segmentsRendered / frameCount) + "/f");

        frameCount = 0;
        rowsRendered = 0;
        rowsSkipped = 0;
        rowsDistSkipped = 0;
        gridSquareLookups = 0;
        gridSquaresSaved = 0;
        segmentsRendered = 0;
    }

    private OptiZombFog() {}
}

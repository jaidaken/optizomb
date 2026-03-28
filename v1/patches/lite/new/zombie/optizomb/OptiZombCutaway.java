package zombie.optizomb;

import zombie.debug.DebugLog;
import zombie.iso.IsoGridSquare;

/**
 * CUTAWAY optimization: caches IsCutawaySquare() results per-square using
 * player tile position as cache key.
 *
 * Vanilla recomputes cutaway for every visible square every frame that
 * SetCutawayRoomsForPlayer() triggers. Since IsCutawaySquare() depends on
 * the relative geometry between the player and the square, results are
 * deterministic when the player hasn't moved tiles.
 */
public final class OptiZombCutaway {

    private static long lastReportTime = System.currentTimeMillis();
    private static int loopCount;
    private static int squareCount;
    private static int cacheHits;
    private static int cacheMisses;

    /** Check if the square's cached cutaway result is still valid for the given player position. */
    public static boolean shouldRecompute(IsoGridSquare sq, int playerX, int playerY) {
        if (sq.cutawayCachedPlayerX == playerX && sq.cutawayCachedPlayerY == playerY) {
            return false;
        }
        return true;
    }

    /** Store the cutaway result in the square's cache. */
    public static void cacheResult(IsoGridSquare sq, int playerX, int playerY, boolean result) {
        sq.cutawayCachedPlayerX = playerX;
        sq.cutawayCachedPlayerY = playerY;
        sq.cutawayCachedResult = result;
        cacheMisses++;
        squareCount++;
    }

    public static void recordCacheHit() {
        cacheHits++;
        squareCount++;
    }

    public static void recordLoopStart() {
        loopCount++;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;
        if (!OptiZombConfig.CUTAWAY) return;
        if (squareCount == 0 && loopCount == 0) return;
        int total = cacheHits + cacheMisses;
        int pct = total > 0 ? (cacheHits * 100 / total) : 0;
        DebugLog.General.println("[OptiZomb] CUTAWAY (5.0s): loops=" + loopCount
                + " squares=" + squareCount
                + " cacheHits=" + cacheHits + " cacheMisses=" + cacheMisses
                + " (" + pct + "% hit)");
        loopCount = 0;
        squareCount = 0;
        cacheHits = 0;
        cacheMisses = 0;
    }

    private OptiZombCutaway() {}
}

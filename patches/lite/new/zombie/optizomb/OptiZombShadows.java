package zombie.optizomb;

import java.util.ArrayList;
import java.util.Comparator;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.debug.DebugLog;

/**
 * OptiZomb Shadow rendering optimization (Opt 27f).
 *
 * Collects all shadow quads (dead bodies, vehicles) into a pooled buffer,
 * sorts by texture ID to minimize sprite batch breaks, then flushes contiguously.
 */
public final class OptiZombShadows {

    public static final class DeferredShadow {
        public Texture texture;
        public float x1, y1, x2, y2, x3, y3, x4, y4;
        public float r, g, b, a;
    }

    private static final ArrayList<DeferredShadow> buffer = new ArrayList<>(1024);
    private static final ArrayList<DeferredShadow> pool = new ArrayList<>(1024);
    private static int poolCursor = 0;

    private static final Comparator<DeferredShadow> TEX_COMP = (a, b) -> {
        int idA = a.texture != null ? a.texture.getID() : -1;
        int idB = b.texture != null ? b.texture.getID() : -1;
        return Integer.compare(idA, idB);
    };

    // Diagnostics
    private static long lastReportTime = 0;
    private static int shadowsDeferred = 0;
    private static int shadowsFlushed = 0;
    private static int uniqueTextures = 0;

    public static DeferredShadow allocShadow() {
        if (poolCursor < pool.size()) {
            return pool.get(poolCursor++);
        }
        DeferredShadow ds = new DeferredShadow();
        pool.add(ds);
        poolCursor++;
        return ds;
    }

    public static void addDeferredShadow(DeferredShadow ds) {
        buffer.add(ds);
        shadowsDeferred++;
    }

    public static void clearBuffer() {
        buffer.clear();
        poolCursor = 0;
    }

    public static void flushSorted() {
        if (buffer.isEmpty()) return;
        buffer.sort(TEX_COMP);

        int texCount = 1;
        int lastTexId = buffer.get(0).texture != null ? buffer.get(0).texture.getID() : -1;

        for (int i = 0; i < buffer.size(); i++) {
            DeferredShadow ds = buffer.get(i);
            SpriteRenderer.instance.renderPoly(
                ds.texture,
                ds.x1, ds.y1, ds.x2, ds.y2,
                ds.x3, ds.y3, ds.x4, ds.y4,
                ds.r, ds.g, ds.b, ds.a
            );

            int texId = ds.texture != null ? ds.texture.getID() : -1;
            if (texId != lastTexId) {
                texCount++;
                lastTexId = texId;
            }
        }

        shadowsFlushed += buffer.size();
        uniqueTextures += texCount;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.SHADOWS) return;
        if (shadowsDeferred == 0) return;

        int batchBreaksSaved = shadowsDeferred - uniqueTextures;
        DebugLog.General.println("[OptiZomb] SHADOWS (5.0s): " + shadowsDeferred + " deferred, "
            + uniqueTextures + " unique textures, ~" + batchBreaksSaved + " batch breaks saved");

        shadowsDeferred = 0;
        shadowsFlushed = 0;
        uniqueTextures = 0;
    }

    private OptiZombShadows() {}
}

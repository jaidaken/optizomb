package zombie.optizomb;

import org.lwjgl.opengl.GL11;
import zombie.debug.DebugLog;

/**
 * TERRAIN optimization: narrowed GL attrib push masks + redundant GL call elimination
 * in HeightTerrain.render().
 *
 * Vanilla pushes ALL client/server attrib bits (0xFFFFFFFF / 0xFFFFF), but HeightTerrain
 * only needs vertex array (0x2) and depth+color+enable+polygon+scissor (0x46108).
 * Also eliminates 5 duplicate GL state calls within the same method.
 */
public final class OptiZombTerrain {

    private static long lastReportTime = System.currentTimeMillis();
    private static int calls;
    private static int redundantSkipped;

    /** Push narrowed attrib masks instead of full-mask vanilla versions. */
    public static void pushNarrowedAttribs() {
        GL11.glPushClientAttrib(0x2);       // GL_CLIENT_VERTEX_ARRAY_BIT only
        GL11.glPushAttrib(0x46108);         // depth + color + enable + polygon + scissor
        calls++;
    }

    public static void recordRedundantSkip() {
        redundantSkipped++;
    }

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;
        if (!OptiZombConfig.TERRAIN) return;
        if (calls == 0) return;
        DebugLog.General.println("[OptiZomb] TERRAIN (5.0s): calls=" + calls
                + " redundantSkipped=" + redundantSkipped);
        calls = 0;
        redundantSkipped = 0;
    }

    private OptiZombTerrain() {}
}

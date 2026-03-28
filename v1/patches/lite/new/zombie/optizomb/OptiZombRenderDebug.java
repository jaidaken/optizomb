package zombie.optizomb;

import zombie.IndieGL;
import zombie.debug.DebugLog;

/**
 * Diagnostic tool for the intermittent rendering disappearance bug.
 *
 * Phase 1: GL state barrier before non-floor rendering.
 * Forces GL state to known-good values before VegetationCorpses/MinusFloorCharacters.
 * If this fixes the bug, the cause is GL state corruption from an optimization
 * that runs between floor rendering and vegetation rendering (blood, shadows, etc.).
 */
public final class OptiZombRenderDebug {

    private static long lastFrameCount = -1;

    // Totals across all layers in a frame
    private static int frameTotalGridStack = 0;
    private static int frameTotalVegetation = 0;
    private static int frameTotalMinusFloor = 0;

    // GL push/pop tracking
    private static int attribPushCount = 0;
    private static int attribPopCount = 0;
    private static int clientAttribPushCount = 0;
    private static int clientAttribPopCount = 0;

    public static void resetFrameTotals(long frameCount) {
        if (frameCount != lastFrameCount) {
            checkPreviousFrame();
            frameTotalGridStack = 0;
            frameTotalVegetation = 0;
            frameTotalMinusFloor = 0;
            lastFrameCount = frameCount;
            attribPushCount = 0;
            attribPopCount = 0;
            clientAttribPushCount = 0;
            clientAttribPopCount = 0;
        }
    }

    public static void recordLayerBucketSizes(int layer, int gridStackSize,
            int vegetationSize, int minusFloorSize, int solidFloorSize, int shadowSize) {
        frameTotalGridStack += gridStackSize;
        frameTotalVegetation += vegetationSize;
        frameTotalMinusFloor += minusFloorSize;
    }

    public static void recordAttribPush() { attribPushCount++; }
    public static void recordAttribPop() { attribPopCount++; }
    public static void recordClientAttribPush() { clientAttribPushCount++; }
    public static void recordClientAttribPop() { clientAttribPopCount++; }

    /**
     * Force GL state to known-good values before non-floor rendering.
     * Uses IndieGL (game-thread safe command buffer) — no direct GL11 calls.
     *
     * This is a diagnostic barrier: if the rendering disappearance bug stops
     * with this enabled, the cause is GL state corruption from an optimization
     * running between floor and vegetation phases (blood, shadows, fog, etc.).
     */
    public static void forceGLStateBarrier() {
        // Reset alpha test to standard sprite rendering state
        IndieGL.enableAlphaTest();
        IndieGL.glAlphaFunc(516, 0.0F);      // GL_GREATER, 0.0

        // Reset blend mode
        IndieGL.glEnable(3042);               // GL_BLEND
        IndieGL.glBlendFunc(770, 771);        // SRC_ALPHA, ONE_MINUS_SRC_ALPHA

        // Ensure color writes are enabled
        IndieGL.glColorMask(true, true, true, true);

        // Reset stencil to pass-all
        IndieGL.glStencilFunc(519, 1, 255);   // GL_ALWAYS, 1, 0xFF
        IndieGL.glStencilOp(7680, 7680, 7680); // GL_KEEP, GL_KEEP, GL_KEEP
    }

    private static void checkPreviousFrame() {
        if (lastFrameCount < 0) return;

        // Detect attrib stack imbalance
        if (attribPushCount != attribPopCount) {
            DebugLog.General.println("[OptiZomb] ATTRIB STACK IMBALANCE at frame " + lastFrameCount
                + " push=" + attribPushCount + " pop=" + attribPopCount);
        }
        if (clientAttribPushCount != clientAttribPopCount) {
            DebugLog.General.println("[OptiZomb] CLIENT ATTRIB STACK IMBALANCE at frame " + lastFrameCount
                + " push=" + clientAttribPushCount + " pop=" + clientAttribPopCount);
        }
    }

    private OptiZombRenderDebug() {}
}

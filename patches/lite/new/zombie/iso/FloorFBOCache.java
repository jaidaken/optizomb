package zombie.iso;

import java.util.ArrayList;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.TextureFBO;

/**
 * Floor FBO Cache — caches rendered floor tiles in offscreen textures.
 *
 * Three frame modes:
 *   BYPASS  — dirty frame, render tiles normally with zero FBO overhead
 *   CAPTURE — first clean frame after dirty, render tiles into FBO for future use
 *   BLIT    — subsequent clean frames, skip tile rendering entirely and blit cached FBO
 *
 * During movement (driving, walking), every frame is BYPASS — zero overhead.
 * When the player stops, one CAPTURE frame runs, then BLIT saves work until they move again.
 *
 * FBO dimensions use IsoCamera.frameState offscreen dimensions (not screen size).
 */
public class FloorFBOCache {
    private static final int MAX_LAYERS = 16;

    /** Frame action returned by evaluateFrame(). */
    public static final int BYPASS = 0;
    public static final int CAPTURE = 1;
    public static final int BLIT = 2;

    // FBO resources — one FBO, texture swapped per layer
    private TextureFBO fbo;
    private final Texture[] layerTextures = new Texture[MAX_LAYERS];
    private int fboWidth, fboHeight;

    // Dirty detection state
    private float cachedOffX = Float.NaN;
    private float cachedOffY = Float.NaN;
    private float cachedZoom;
    private int cachedTileScale;
    private int cachedW, cachedH;
    private int cachedRoomId;
    private int cachedLayerCount;
    private boolean forceInvalidate = true;
    private int warmupFrames = 3;

    // Bypass: track whether we have a valid capture to blit
    private boolean captured = false;

    // Cached per-layer grid stack sizes
    private final int[] cachedGridStackSize = new int[MAX_LAYERS];

    // Cached bucket lists per layer
    @SuppressWarnings("unchecked")
    private final ArrayList<IsoGridSquare>[] cachedSolidFloor = new ArrayList[MAX_LAYERS];
    @SuppressWarnings("unchecked")
    private final ArrayList<IsoGridSquare>[] cachedShadedFloor = new ArrayList[MAX_LAYERS];
    @SuppressWarnings("unchecked")
    private final ArrayList<IsoGridSquare>[] cachedVegetationCorpses = new ArrayList[MAX_LAYERS];
    @SuppressWarnings("unchecked")
    private final ArrayList<IsoGridSquare>[] cachedMinusFloorCharacters = new ArrayList[MAX_LAYERS];
    @SuppressWarnings("unchecked")
    private final ArrayList<IsoGridSquare>[] cachedShadowSquares = new ArrayList[MAX_LAYERS];

    // Two minimal GenericDrawers — just FBO bind/unbind, no blend state changes
    private final CaptureBeginDrawer captureBeginDrawer = new CaptureBeginDrawer();
    private final CaptureEndDrawer captureEndDrawer = new CaptureEndDrawer();

    public FloorFBOCache() {
        for (int i = 0; i < MAX_LAYERS; i++) {
            cachedSolidFloor[i] = new ArrayList<>();
            cachedShadedFloor[i] = new ArrayList<>();
            cachedVegetationCorpses[i] = new ArrayList<>();
            cachedMinusFloorCharacters[i] = new ArrayList<>();
            cachedShadowSquares[i] = new ArrayList<>();
        }
    }

    // ── Diagnostics ──────────────────────────────────────
    private static int diagForce, diagWarmup, diagOff, diagScale, diagZoom, diagSize;
    private static int diagRoom, diagDirtyTime, diagLight, diagGridSize, diagBucket;
    private static int diagClean, diagBypass, diagCapture, diagTotal;

    /**
     * Evaluate what this frame should do. Call once per frame before the layer loop.
     *
     * Returns BYPASS (dirty, no FBO), CAPTURE (first clean, render+capture), or BLIT (cached).
     */
    public int evaluateFrame(int layerCount, float playerDirtyRecalcGridStackTime,
                             ArrayList<ArrayList<IsoGridSquare>> layerGridStacks, int numLayers,
                             int playerIndex) {
        diagTotal++;
        boolean dirty = isDirty(layerCount, playerDirtyRecalcGridStackTime,
                                layerGridStacks, numLayers, playerIndex);
        if (dirty) {
            captured = false;
            diagBypass++;
            return BYPASS;
        } else if (captured) {
            diagClean++;
            return BLIT;
        } else {
            diagCapture++;
            return CAPTURE;
        }
    }

    private boolean isDirty(int layerCount, float playerDirtyRecalcGridStackTime,
                            ArrayList<ArrayList<IsoGridSquare>> layerGridStacks, int numLayers,
                            int playerIndex) {
        if (forceInvalidate) { diagForce++; return true; }
        if (warmupFrames > 0) { diagWarmup++; return true; }

        IsoCamera.FrameState fs = IsoCamera.frameState;
        if (fs.OffX != cachedOffX || fs.OffY != cachedOffY) { diagOff++; return true; }
        if (Core.TileScale != cachedTileScale) { diagScale++; return true; }
        float zoom = Core.getInstance().getZoom(playerIndex);
        if (zoom != cachedZoom) { diagZoom++; return true; }
        if (fs.OffscreenWidth != cachedW || fs.OffscreenHeight != cachedH) { diagSize++; return true; }
        if (layerCount != cachedLayerCount) { diagSize++; return true; }

        int roomId = IsoWorld.instance.CurrentCell.GetEffectivePlayerRoomId();
        if (roomId != cachedRoomId) { diagRoom++; return true; }

        if (playerDirtyRecalcGridStackTime > 0.0F) { diagDirtyTime++; return true; }
        if (IsoGridSquare.RecalcLightTime > 0) { diagLight++; return true; }

        for (int layer = 0; layer < numLayers; layer++) {
            ArrayList<IsoGridSquare> gs = layerGridStacks.get(layer);
            if (gs == null) continue;
            int size = gs.size();
            if (size != cachedGridStackSize[layer]) { diagGridSize++; return true; }
            for (int i = 0; i < size; i++) {
                if (gs.get(i).staticBucketDirty) { diagBucket++; return true; }
            }
        }

        return false;
    }

    public static String getDiagAndReset() {
        String s = "force=" + diagForce + " warmup=" + diagWarmup
            + " off=" + diagOff + " scale=" + diagScale + " zoom=" + diagZoom
            + " size=" + diagSize + " room=" + diagRoom
            + " dirtyTime=" + diagDirtyTime + " light=" + diagLight
            + " gridSize=" + diagGridSize + " bucket=" + diagBucket
            + " bypass=" + diagBypass + " capture=" + diagCapture
            + " blit=" + diagClean + "/" + diagTotal;
        diagForce = diagWarmup = diagOff = diagScale = diagZoom = diagSize = 0;
        diagRoom = diagDirtyTime = diagLight = diagGridSize = diagBucket = 0;
        diagClean = diagBypass = diagCapture = diagTotal = 0;
        return s;
    }

    /**
     * Update cached comparison state. Call after every BYPASS and CAPTURE frame
     * (not BLIT — nothing changed on BLIT frames).
     */
    public void updateCachedState(int layerCount, int playerIndex) {
        IsoCamera.FrameState fs = IsoCamera.frameState;
        cachedOffX = fs.OffX;
        cachedOffY = fs.OffY;
        cachedZoom = Core.getInstance().getZoom(playerIndex);
        cachedTileScale = Core.TileScale;
        cachedW = fs.OffscreenWidth;
        cachedH = fs.OffscreenHeight;
        cachedRoomId = IsoWorld.instance.CurrentCell.GetEffectivePlayerRoomId();
        cachedLayerCount = layerCount;
        forceInvalidate = false;
        if (warmupFrames > 0) warmupFrames--;
    }

    /** Mark the cache as valid after a CAPTURE frame completes. */
    public void markCaptured() {
        captured = true;
    }

    // ── FBO management ───────────────────────────────────

    private void ensureFBO(int layer) {
        IsoCamera.FrameState fs = IsoCamera.frameState;
        int w = fs.OffscreenWidth;
        int h = fs.OffscreenHeight;
        if (w <= 0 || h <= 0) return;

        if (fbo != null && (fboWidth != w || fboHeight != h)) {
            fbo.destroy();
            fbo = null;
            for (int i = 0; i < MAX_LAYERS; i++) {
                layerTextures[i] = null;
            }
            fboWidth = 0;
            fboHeight = 0;
            warmupFrames = 3;
            captured = false;
        }

        if (layerTextures[layer] == null) {
            layerTextures[layer] = new Texture(w, h, 16);
        }

        if (fbo == null) {
            fbo = new TextureFBO(layerTextures[layer], false);
            fboWidth = w;
            fboHeight = h;
        }
    }

    // ── Capture (CAPTURE frames only) ────────────────────

    public void beginCapture(int layer) {
        ensureFBO(layer);
        captureBeginDrawer.texture = layerTextures[layer];
        SpriteRenderer.instance.drawGeneric(captureBeginDrawer);
    }

    public void endCapture(int layer) {
        SpriteRenderer.instance.drawGeneric(captureEndDrawer);
    }

    // ── Blit (CAPTURE and BLIT frames) ───────────────────

    public void blitCached(int layer) {
        Texture tex = layerTextures[layer];
        if (tex == null) return;
        tex.rendershader2(0, 0, fboWidth, fboHeight,
                          0, 0, fboWidth, fboHeight,
                          1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ── Bucket save/restore ──────────────────────────────

    public void saveBuckets(int layer,
                            ArrayList<IsoGridSquare> solidFloor,
                            ArrayList<IsoGridSquare> shadedFloor,
                            ArrayList<IsoGridSquare> vegCorpses,
                            ArrayList<IsoGridSquare> minusFloorChars,
                            ArrayList<IsoGridSquare> shadowSquares) {
        copyList(solidFloor, cachedSolidFloor[layer]);
        copyList(shadedFloor, cachedShadedFloor[layer]);
        copyList(vegCorpses, cachedVegetationCorpses[layer]);
        copyList(minusFloorChars, cachedMinusFloorCharacters[layer]);
        copyList(shadowSquares, cachedShadowSquares[layer]);
    }

    public void restoreBuckets(int layer,
                               ArrayList<IsoGridSquare> solidFloor,
                               ArrayList<IsoGridSquare> shadedFloor,
                               ArrayList<IsoGridSquare> vegCorpses,
                               ArrayList<IsoGridSquare> minusFloorChars,
                               ArrayList<IsoGridSquare> shadowSquares) {
        solidFloor.addAll(cachedSolidFloor[layer]);
        shadedFloor.addAll(cachedShadedFloor[layer]);
        vegCorpses.addAll(cachedVegetationCorpses[layer]);
        minusFloorChars.addAll(cachedMinusFloorCharacters[layer]);
        shadowSquares.addAll(cachedShadowSquares[layer]);
    }

    public void saveGridStackSize(int layer, int size) {
        cachedGridStackSize[layer] = size;
    }

    // ── Lifecycle ────────────────────────────────────────

    public void invalidate() {
        forceInvalidate = true;
        captured = false;
    }

    public void destroy() {
        if (fbo != null) {
            fbo.destroy();
            fbo = null;
        }
        for (int i = 0; i < MAX_LAYERS; i++) {
            layerTextures[i] = null;
        }
        fboWidth = 0;
        fboHeight = 0;
        forceInvalidate = true;
        captured = false;
    }

    // ── Helpers ──────────────────────────────────────────

    private static void copyList(ArrayList<IsoGridSquare> src, ArrayList<IsoGridSquare> dst) {
        dst.clear();
        dst.ensureCapacity(src.size());
        dst.addAll(src);
    }

    // ── GenericDrawers ───────────────────────────────────

    private class CaptureBeginDrawer extends TextureDraw.GenericDrawer {
        Texture texture;

        @Override
        public void render() {
            if (fbo == null || texture == null) return;
            if (fbo.getTexture() != texture) {
                fbo.setTexture(texture);
            }
            fbo.startDrawing(true, true);
        }
    }

    private class CaptureEndDrawer extends TextureDraw.GenericDrawer {
        @Override
        public void render() {
            if (fbo == null) return;
            fbo.endDrawing();
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        }
    }
}

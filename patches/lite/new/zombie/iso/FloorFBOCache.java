package zombie.iso;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.TextureFBO;

public class FloorFBOCache {
    private static final int MAX_LAYERS = 16;

    // FBO resources
    private TextureFBO fbo;
    private final Texture[] layerTextures = new Texture[MAX_LAYERS];
    private int fboWidth, fboHeight;

    // Cached dirty detection state
    private float cachedOffX = Float.NaN;
    private float cachedOffY = Float.NaN;
    private float cachedZoom;
    private int cachedTileScale;
    private int cachedW, cachedH;
    private int cachedRoomId;
    private int cachedLayerCount;
    private boolean forceInvalidate = true;
    private int warmupFrames = 2;

    // Cached per-square renderFloorInternal return values
    private final int[][] cachedFloorBits = new int[MAX_LAYERS][];
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

    // Per-layer GenericDrawer instances for FBO bind/unbind
    private final FBOStartDrawer[] startDrawers = new FBOStartDrawer[MAX_LAYERS];
    private final FBOEndDrawer[] endDrawers = new FBOEndDrawer[MAX_LAYERS];

    // Shared GenericDrawers for blit setup/restore (alpha test + nearest filtering)
    private final BlitSetupDrawer blitSetup = new BlitSetupDrawer();
    private final BlitRestoreDrawer blitRestore = new BlitRestoreDrawer();
    private Texture currentBlitTexture; // set by blitCached before queueing drawers

    public FloorFBOCache() {
        for (int i = 0; i < MAX_LAYERS; i++) {
            cachedSolidFloor[i] = new ArrayList<>();
            cachedShadedFloor[i] = new ArrayList<>();
            cachedVegetationCorpses[i] = new ArrayList<>();
            cachedMinusFloorCharacters[i] = new ArrayList<>();
            cachedShadowSquares[i] = new ArrayList<>();
            startDrawers[i] = new FBOStartDrawer(i);
            endDrawers[i] = new FBOEndDrawer(i);
        }
    }

    // Opt 52: diagnostic — track which condition triggers dirty
    private static int diagForce, diagWarmup, diagOff, diagScale, diagZoom, diagSize;
    private static int diagRoom, diagDirtyTime, diagLight, diagGridSize, diagBucket;
    private static int diagClean, diagTotal;

    public boolean isDirty(int layerCount, float playerDirtyRecalcGridStackTime,
                           ArrayList<ArrayList<IsoGridSquare>> layerGridStacks, int numLayers,
                           int playerIndex) {
        diagTotal++;
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

        diagClean++;
        return false;
    }

    public static String getDiagAndReset() {
        String s = "force=" + diagForce + " warmup=" + diagWarmup
            + " off=" + diagOff + " scale=" + diagScale + " zoom=" + diagZoom
            + " size=" + diagSize + " room=" + diagRoom
            + " dirtyTime=" + diagDirtyTime + " light=" + diagLight
            + " gridSize=" + diagGridSize + " bucket=" + diagBucket
            + " clean=" + diagClean + "/" + diagTotal;
        diagForce = diagWarmup = diagOff = diagScale = diagZoom = diagSize = 0;
        diagRoom = diagDirtyTime = diagLight = diagGridSize = diagBucket = 0;
        diagClean = diagTotal = 0;
        return s;
    }

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

    /**
     * Ensure FBO and texture are allocated at the GL viewport size (screen dimensions).
     */
    private void ensureFBO(int layer) {
        int w = Core.getInstance().getScreenWidth();
        int h = Core.getInstance().getScreenHeight();
        if (IsoPlayer.numPlayers > 1) w /= 2;
        if (IsoPlayer.numPlayers > 2) h /= 2;

        if (fbo != null && (fboWidth != w || fboHeight != h)) {
            fbo.destroy();
            fbo = null;
            for (int i = 0; i < MAX_LAYERS; i++) {
                layerTextures[i] = null;
            }
            fboWidth = 0;
            fboHeight = 0;
            warmupFrames = 2;
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

    /**
     * Bind FBO and clear to transparent before floor tile rendering (dirty frame).
     */
    public void beginCapture(int layer) {
        ensureFBO(layer);
        startDrawers[layer].texture = layerTextures[layer];
        SpriteRenderer.instance.drawGeneric(startDrawers[layer]);
    }

    /**
     * Unbind FBO after floor tile rendering (dirty frame).
     */
    public void endCapture(int layer) {
        SpriteRenderer.instance.drawGeneric(endDrawers[layer]);
    }

    /**
     * Blit the cached FBO texture to screen.
     *
     * Uses alpha-test + opaque copy to avoid tile edge artifacts:
     * - Pixels with alpha > threshold: drawn as opaque (GL_ONE, GL_ZERO) — no dest bleed
     * - Pixels with alpha <= threshold (empty gaps): discarded — dest preserved
     *
     * This eliminates both dark edges (from double-premultiplication with standard blend)
     * and bright edges (from dest bleeding with premultiplied blend).
     */
    public void blitCached(int layer) {
        Texture tex = layerTextures[layer];
        if (tex == null) return;

        // Set texture ref for the setup drawer to apply GL_NEAREST filtering
        currentBlitTexture = tex;

        // GenericDrawer: enable alpha test + opaque blend + nearest filtering
        SpriteRenderer.instance.drawGeneric(blitSetup);

        // Blit: cover full projection range, read full FBO content
        int projW = IsoCamera.frameState.OffscreenWidth;
        int projH = IsoCamera.frameState.OffscreenHeight;
        tex.rendershader2(0, 0, projW, projH,
                          0, 0, fboWidth, fboHeight,
                          1.0F, 1.0F, 1.0F, 1.0F);

        // GenericDrawer: restore alpha test + blend mode
        SpriteRenderer.instance.drawGeneric(blitRestore);
    }

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
        if (cachedFloorBits[layer] == null || cachedFloorBits[layer].length < size) {
            cachedFloorBits[layer] = new int[Math.max(size, 256)];
        }
    }

    public void saveFloorBit(int layer, int squareIndex, int bits) {
        cachedFloorBits[layer][squareIndex] = bits;
    }

    public int getCachedFloorBits(int layer, int squareIndex) {
        if (cachedFloorBits[layer] != null && squareIndex < cachedFloorBits[layer].length) {
            return cachedFloorBits[layer][squareIndex];
        }
        return 0;
    }

    public void invalidate() {
        forceInvalidate = true;
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
    }

    private static void copyList(ArrayList<IsoGridSquare> src, ArrayList<IsoGridSquare> dst) {
        dst.clear();
        dst.ensureCapacity(src.size());
        dst.addAll(src);
    }

    // ──────────────────────────────────────────────────────
    // GenericDrawer: FBO start (bind FBO, clear to transparent)
    // ──────────────────────────────────────────────────────
    private class FBOStartDrawer extends TextureDraw.GenericDrawer {
        final int layer;
        Texture texture;

        FBOStartDrawer(int layer) {
            this.layer = layer;
        }

        @Override
        public void render() {
            if (fbo == null || texture == null) return;
            if (fbo.getTexture() != texture) {
                fbo.setTexture(texture);
            }
            fbo.startDrawing(true, true);
            // Fix alpha squaring: standard SRC_ALPHA,ONE_MINUS_SRC_ALPHA applies
            // SRC_ALPHA to the alpha channel too, giving alpha² instead of alpha.
            // Use separate blend: RGB stays normal, alpha uses GL_ONE to preserve
            // correct src_a values for accurate premultiplied blit later.
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                                     GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    // ──────────────────────────────────────────────────────
    // GenericDrawer: FBO end (unbind FBO)
    // ──────────────────────────────────────────────────────
    private class FBOEndDrawer extends TextureDraw.GenericDrawer {
        final int layer;

        FBOEndDrawer(int layer) {
            this.layer = layer;
        }

        @Override
        public void render() {
            if (fbo == null) return;
            // Restore standard blend before unbinding FBO
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            fbo.endDrawing();
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        }
    }

    // ──────────────────────────────────────────────────────
    // GenericDrawer: Blit setup — premultiplied blend + nearest filtering
    // With premultiplied blend, transparent pixels (0,0,0,0) naturally preserve
    // destination: result = 0 + dest * 1 = dest. No alpha test needed.
    // ──────────────────────────────────────────────────────
    private class BlitSetupDrawer extends TextureDraw.GenericDrawer {
        @Override
        public void render() {
            // GL_NEAREST prevents bilinear filtering from blending tile edge
            // texels with adjacent transparent texels (which would darken edges)
            Texture tex = currentBlitTexture;
            if (tex != null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getID());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    // ──────────────────────────────────────────────────────
    // GenericDrawer: Blit restore — restore blend + filtering
    // ──────────────────────────────────────────────────────
    private class BlitRestoreDrawer extends TextureDraw.GenericDrawer {
        @Override
        public void render() {
            Texture tex = currentBlitTexture;
            if (tex != null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getID());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        }
    }
}

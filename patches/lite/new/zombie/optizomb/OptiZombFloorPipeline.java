package zombie.optizomb;

import java.util.function.Consumer;
import zombie.core.Color;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.iso.IsoCamera;
import zombie.iso.IsoObject;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.iso.sprite.IsoAnim;
import zombie.iso.sprite.IsoDirectionFrame;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.iso.sprite.shapers.FloorShaper;
import zombie.iso.IsoObjectPicker;
import zombie.iso.sprite.shapers.FloorShaperAttachedSprites;
import zombie.iso.sprite.shapers.FloorShaperDeDiamond;
import zombie.iso.sprite.shapers.FloorShaperDiamond;

/**
 * OptiZomb Floor Pipeline optimization (Opt 20).
 *
 * Sub-optimizations:
 *   20a: Hoist frame-invariant values out of per-square loop
 *   20b: Cache building occlusion result per-square per-frame
 *   20c: Strip profiler wrapper — call renderFloorInternal directly
 *   20d: Cache static bucket bits in byte bitmask on IsoGridSquare
 *   20e: One StartShader/EndShader pair per z-layer instead of per-tile
 *   20f: Cache floorLayer uniform location (bypass per-tile HashMap lookup)
 *   20g: Direct FloorShaper accept (eliminate Type.tryCastTo per tile)
 *   20h: Hoist FloorShaper config outside object loop
 */
public final class OptiZombFloorPipeline {

    // Diagnostics — counters
    private static long lastReportTime = 0;
    private static int frameCount = 0;
    private static int layerCount = 0;
    private static int fboCleanLayers = 0;
    private static int attachedSprites = 0;
    private static int mainSpriteSkips = 0;

    // Diagnostics — per-layer timing (nanos accumulated across frames)
    private static long renderLoopNs = 0;
    private static long fboCheckNs = 0;
    private static long envNs = 0;

    // --- 20d: Static bucket bit constants ---
    public static final byte SB_STATIC_MOVING = 0x01;
    public static final byte SB_HAS_STAIRS    = 0x02;
    public static final byte SB_WORLD_OBJECTS  = 0x04;
    public static final byte SB_TEMP_OBJECTS   = 0x08;
    public static final byte SB_DEFERRED_CHARS = 0x10;
    public static final byte SB_HAS_FLIES      = 0x20;

    public static void recomputeStaticBucketBits(IsoGridSquare sq) {
        byte bits = 0;
        if (!sq.getStaticMovingObjects().isEmpty()) bits |= SB_STATIC_MOVING;
        if (sq.HasStairs())                         bits |= SB_HAS_STAIRS;
        if (!sq.getWorldObjects().isEmpty())        bits |= SB_WORLD_OBJECTS;
        if (!sq.getLocalTemporaryObjects().isEmpty()) bits |= SB_TEMP_OBJECTS;
        if (!sq.getDeferedCharacters().isEmpty())   bits |= SB_DEFERRED_CHARS;
        if (sq.hasFlies())                          bits |= SB_HAS_FLIES;
        sq.staticBucketBits = bits;
        sq.staticBucketDirty = false;
    }

    // --- 20d: Translate cached static bucket bits into floor bucket flags ---

    public static int translateStaticBucketBits(byte sbBits) {
        int flags = 0;
        if ((sbBits & SB_STATIC_MOVING) != 0) {
            flags |= 2 | 16;
            if ((sbBits & SB_HAS_STAIRS) != 0) flags |= 4;
        }
        if ((sbBits & SB_WORLD_OBJECTS) != 0) flags |= 2;
        if ((sbBits & SB_TEMP_OBJECTS) != 0) flags |= 4;
        if ((sbBits & SB_DEFERRED_CHARS) != 0) flags |= 4;
        if ((sbBits & SB_HAS_FLIES) != 0) flags |= 4;
        return flags;
    }

    // --- 20h: Configure all three FloorShaper instances with lighting + water ---

    public static void configureShapers(IsoGridSquare sq, int playerIndex, boolean shore,
            float depth0, float depth1, float depth2, float depth3,
            FloorShaperAttachedSprites attachedShaper) {
        int v0 = sq.getVertLight(0, playerIndex);
        int v1 = sq.getVertLight(1, playerIndex);
        int v2 = sq.getVertLight(2, playerIndex);
        int v3 = sq.getVertLight(3, playerIndex);
        if (DebugOptions.instance.Terrain.RenderTiles.IsoGridSquare.Floor.LightingDebug.getValue()) {
            v0 = -65536;
            v1 = -65536;
            v2 = -16776961;
            v3 = -16776961;
        }
        attachedShaper.setShore(shore);
        attachedShaper.setWaterDepth(depth0, depth1, depth2, depth3);
        attachedShaper.setVertColors(v0, v1, v2, v3);
        FloorShaperDiamond.instance.setShore(shore);
        FloorShaperDiamond.instance.setWaterDepth(depth0, depth1, depth2, depth3);
        FloorShaperDiamond.instance.setVertColors(v0, v1, v2, v3);
        FloorShaperDeDiamond.instance.setShore(shore);
        FloorShaperDeDiamond.instance.setWaterDepth(depth0, depth1, depth2, depth3);
        FloorShaperDeDiamond.instance.setVertColors(v0, v1, v2, v3);
    }

    // --- Opt 49: Direct floor tile render (flattened call chain) ---

    private static final ColorInfo directInfo = new ColorInfo();
    private static int directRenders = 0;
    private static int fallbackRenders = 0;

    /**
     * Render a floor tile sprite directly to SpriteRenderer, bypassing
     * the 5-level IsoObject → IsoSprite → renderCurrentAnim → prepareToRenderSprite
     * → performRenderFrame → directionFrame.render → Texture.render chain.
     *
     * Returns true if direct render was used, false if caller should fall back
     * to the vanilla renderFloorTile() path.
     */
    public static boolean renderFloorTileDirect(
            IsoObject object, ColorInfo floorColorInfo,
            int shaderID, int floorLayerLoc,
            FloorShaper mainShaper) {
        IsoSprite sprite = object.sprite;
        if (sprite == null) { fallbackRenders++; return false; }

        // Bail to vanilla for edge cases
        IsoAnim anim = sprite.CurrentAnim;
        if (anim == null || anim.FramesArray == null || anim.FramesArray.length == 0) {
            fallbackRenders++; return false;
        }
        if (sprite.hasActiveModel()) { fallbackRenders++; return false; }
        if (object.isHighlighted()) { fallbackRenders++; return false; }
        if (object.getCustomColor() != null) { fallbackRenders++; return false; }
        if (sprite.forceAmbient) { fallbackRenders++; return false; }

        // 1. Get texture directly (eliminates getTexture + redundant lookup)
        IsoDirectionFrame frame = anim.FramesArray[0];
        Texture texture = frame.directions[object.dir.index()];
        if (texture == null) { fallbackRenders++; return false; }

        // 2. Alpha interpolation (replaces spriteInstance.renderprep)
        IsoSpriteInstance si = sprite.def;
        si.prepAlpha(object);

        // 3. Screen position (replaces prepareToRenderSprite)
        float sx, sy;
        if (IsoSprite.globalOffsetX == -1.0F) {
            IsoSprite.globalOffsetX = -IsoCamera.frameState.OffX;
            IsoSprite.globalOffsetY = -IsoCamera.frameState.OffY;
        }
        if (object.sx != 0.0F) {
            sx = object.sx + IsoSprite.globalOffsetX + sprite.soffX;
            sy = object.sy + IsoSprite.globalOffsetY + sprite.soffY;
        } else {
            float rawX = IsoUtils.XToScreen(
                object.getSquare().getX() + si.offX,
                object.getSquare().getY() + si.offY,
                object.getSquare().getZ() + si.offZ, 0);
            float rawY = IsoUtils.YToScreen(
                object.getSquare().getX() + si.offX,
                object.getSquare().getY() + si.offY,
                object.getSquare().getZ() + si.offZ, 0);
            rawX -= object.offsetX;
            rawY -= (object.offsetY + object.getRenderYOffset() * Core.TileScale);
            object.sx = rawX;
            object.sy = rawY;
            sx = rawX + IsoSprite.globalOffsetX + sprite.soffX;
            sy = rawY + IsoSprite.globalOffsetY + sprite.soffY;
        }

        // 4. Color setup (replaces prepareToRender + renderCurrentAnim color backup)
        directInfo.set(floorColorInfo);
        if (si.tintr != 1.0F || si.tintg != 1.0F || si.tintb != 1.0F) {
            directInfo.r *= si.tintr;
            directInfo.g *= si.tintg;
            directInfo.b *= si.tintb;
        }
        directInfo.a = si.alpha;
        if (si.bMultiplyObjectAlpha) {
            directInfo.a *= object.getAlpha(IsoCamera.frameState.playerIndex);
        }
        if (sprite.TintMod.r != 1.0F || sprite.TintMod.g != 1.0F || sprite.TintMod.b != 1.0F) {
            directInfo.r *= sprite.TintMod.r;
            directInfo.g *= sprite.TintMod.g;
            directInfo.b *= sprite.TintMod.b;
        }

        // 5. Shader uniform (replaces ShaderUpdate1i in renderFloorTile)
        if (floorLayerLoc >= 0) {
            zombie.IndieGL.ShaderUpdate1i(shaderID, floorLayerLoc, 0);
        }

        // 6. Submit directly to SpriteRenderer (replaces directionFrame.render → Texture.render)
        float w = texture.getWidth();
        float h = texture.getHeight();
        SpriteRenderer.instance.render(
            texture,
            sx + texture.getOffsetX(), sy + texture.getOffsetY(),
            w, h,
            directInfo.r, directInfo.g, directInfo.b, directInfo.a,
            mainShaper
        );

        // 7. Register with object picker (mirrors IsoSprite.render lines 817-844)
        if (IsoObjectPicker.Instance.wasDirty && IsoCamera.frameState.playerIndex == 0) {
            float psx = object.sx + IsoSprite.globalOffsetX;
            float psy = object.sy + IsoSprite.globalOffsetY;
            IsoObjectPicker.Instance.Add(
                (int) psx, (int) psy,
                (int) texture.getWidthOrig(), (int) texture.getHeightOrig(),
                object.square, object, false, 1.0F, 1.0F
            );
        }

        directRenders++;
        return true;
    }

    // --- Opt 51: Direct attached sprite render (flattened call chain) ---

    private static int directAttached = 0;
    private static int fallbackAttached = 0;

    /**
     * Render an attached sprite directly, bypassing the IsoSprite.render chain.
     * Uses the parent object's cached screen position (set during main sprite render).
     */
    public static boolean renderAttachedSpriteDirect(
            IsoSpriteInstance spriteInstance, IsoObject parentObject,
            ColorInfo colorInfo, int shaderID, int floorLayerLoc,
            Consumer<TextureDraw> consumer) {
        IsoSprite sprite = spriteInstance.parentSprite;
        if (sprite == null) { fallbackAttached++; return false; }
        IsoAnim anim = sprite.CurrentAnim;
        if (anim == null || anim.FramesArray == null || anim.FramesArray.length == 0) {
            fallbackAttached++; return false;
        }
        if (sprite.hasActiveModel()) { fallbackAttached++; return false; }
        if (parentObject.getObjectRenderEffectsToApply() != null) { fallbackAttached++; return false; }

        // Get frame (attached sprites may be animated)
        int frameIdx = (int) spriteInstance.Frame;
        if (frameIdx >= anim.FramesArray.length) frameIdx = anim.FramesArray.length - 1;
        if (frameIdx < 0) frameIdx = 0;
        IsoDirectionFrame frame = anim.FramesArray[frameIdx];
        Texture texture = frame.directions[parentObject.dir.index()];
        if (texture == null) { fallbackAttached++; return false; }

        // Bail if non-standard scale
        if (spriteInstance.scaleX != 1.0F || spriteInstance.scaleY != 1.0F) {
            fallbackAttached++; return false;
        }
        // TileScale==2 rescale check
        if (Core.TileScale == 2 && texture.getWidthOrig() == 64 && texture.getHeightOrig() == 128) {
            fallbackAttached++; return false;
        }

        // Alpha prep
        spriteInstance.prepAlpha(parentObject);

        // Screen position: parent's cached sx/sy + globalOffset + sprite offset
        if (IsoSprite.globalOffsetX == -1.0F) {
            IsoSprite.globalOffsetX = -IsoCamera.frameState.OffX;
            IsoSprite.globalOffsetY = -IsoCamera.frameState.OffY;
        }
        float sx = parentObject.sx + IsoSprite.globalOffsetX + sprite.soffX;
        float sy = parentObject.sy + IsoSprite.globalOffsetY + sprite.soffY;

        // Set floor layer to 1 (attached)
        if (floorLayerLoc >= 0) {
            zombie.IndieGL.ShaderUpdate1i(shaderID, floorLayerLoc, 1);
        }

        // Color: copy base, apply tint + alpha
        directInfo.set(colorInfo);
        directInfo.a = spriteInstance.alpha;
        if (spriteInstance.bMultiplyObjectAlpha) {
            directInfo.a *= parentObject.getAlpha(IsoCamera.frameState.playerIndex);
        }
        if (spriteInstance.tintr != 1.0F || spriteInstance.tintg != 1.0F || spriteInstance.tintb != 1.0F) {
            directInfo.r *= spriteInstance.tintr;
            directInfo.g *= spriteInstance.tintg;
            directInfo.b *= spriteInstance.tintb;
        }
        if (sprite.TintMod.r != 1.0F || sprite.TintMod.g != 1.0F || sprite.TintMod.b != 1.0F) {
            directInfo.r *= sprite.TintMod.r;
            directInfo.g *= sprite.TintMod.g;
            directInfo.b *= sprite.TintMod.b;
        }

        float w = texture.getWidth();
        float h = texture.getHeight();
        SpriteRenderer.instance.render(texture,
            sx + texture.getOffsetX(), sy + texture.getOffsetY(),
            w, h, directInfo.r, directInfo.g, directInfo.b, directInfo.a, consumer);

        // Register with object picker (mirrors IsoSprite.render lines 817-844)
        if (IsoObjectPicker.Instance.wasDirty && IsoCamera.frameState.playerIndex == 0) {
            float psx = parentObject.sx + IsoSprite.globalOffsetX;
            float psy = parentObject.sy + IsoSprite.globalOffsetY;
            IsoObjectPicker.Instance.Add(
                (int) psx, (int) psy,
                (int) texture.getWidthOrig(), (int) texture.getHeightOrig(),
                parentObject.square, parentObject, false, 1.0F, 1.0F
            );
        }

        directAttached++;
        return true;
    }

    // --- Diagnostic counters ---

    public static void recordAttachedSprite() {
        attachedSprites++;
    }

    public static void recordMainSpriteSkip() {
        mainSpriteSkips++;
    }

    public static void recordLayer(boolean fboClean) {
        layerCount++;
        if (fboClean) fboCleanLayers++;
    }

    public static void recordRenderLoopNs(long ns) {
        renderLoopNs += ns;
    }

    public static void recordFboCheckNs(long ns) {
        fboCheckNs += ns;
    }

    public static void recordEnvNs(long ns) {
        envNs += ns;
    }

    public static void recordFrame() {
        frameCount++;
    }

    // --- Diagnostics ---

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.FLOOR_PIPELINE) return;
        if (frameCount == 0) return;

        float avgRenderMs = (renderLoopNs / 1_000_000.0F) / frameCount;
        float avgFboMs = (fboCheckNs / 1_000_000.0F) / frameCount;
        float avgEnvMs = (envNs / 1_000_000.0F) / frameCount;
        float avgTotalMs = avgRenderMs + avgFboMs + avgEnvMs;

        DebugLog.General.println("[OptiZomb] FLOOR_PIPELINE (5.0s): " + frameCount + " frames"
            + " | total=" + String.format("%.2f", avgTotalMs) + "ms/f"
            + " render=" + String.format("%.2f", avgRenderMs) + "ms"
            + " fbo=" + String.format("%.2f", avgFboMs) + "ms"
            + " env=" + String.format("%.2f", avgEnvMs) + "ms"
            + " | fboClean=" + fboCleanLayers + "/" + layerCount
            + " attached=" + (attachedSprites / frameCount)
            + " mainSkip=" + (mainSpriteSkips / frameCount)
            + " direct=" + (directRenders / frameCount)
            + " fallback=" + (fallbackRenders / frameCount)
            + " directAtt=" + (directAttached / frameCount)
            + " fallbackAtt=" + (fallbackAttached / frameCount));

        frameCount = 0;
        layerCount = 0;
        fboCleanLayers = 0;
        renderLoopNs = 0;
        fboCheckNs = 0;
        envNs = 0;
        attachedSprites = 0;
        mainSpriteSkips = 0;
        directRenders = 0;
        fallbackRenders = 0;
        directAttached = 0;
        fallbackAttached = 0;
    }

    private OptiZombFloorPipeline() {}
}

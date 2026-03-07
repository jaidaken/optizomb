package zombie.optizomb;

import org.lwjgl.opengl.GL11;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;
import zombie.debug.DebugLog;

public final class OptiZombGLChars {

    private static boolean confirmed = false;

    // One-shot confirmations for each optimization path
    private static boolean confirmedTexCache = false;
    private static boolean confirmedLightOpt = false;
    private static boolean confirmedUniformOpt = false;

    // Diagnostic counters (reset every reporting interval)
    private static int texCacheHits = 0;
    private static int texCacheMisses = 0;
    private static int lightEarlyOuts = 0;
    private static int lightsProcessed = 0;
    private static int uniformBypass = 0;
    private static long lastReportTime = 0;

    public static void onTextureCacheHit() {
        texCacheHits++;
        if (!confirmedTexCache) {
            confirmedTexCache = true;
            DebugLog.General.println("[OptiZomb] GL_CHARS active: character texture caching enabled");
        }
    }

    public static void onTextureCacheMiss() {
        texCacheMisses++;
        if (!confirmedTexCache) {
            confirmedTexCache = true;
            DebugLog.General.println("[OptiZomb] GL_CHARS active: character texture caching enabled");
        }
    }

    public static void onLightEarlyOut() {
        lightEarlyOuts++;
    }

    public static void onLightProcessed() {
        lightsProcessed++;
        if (!confirmedLightOpt) {
            confirmedLightOpt = true;
            DebugLog.General.println("[OptiZomb] GL_CHARS active: precomputed light setup with array lookups enabled");
        }
    }

    public static void onUniformBypass() {
        uniformBypass++;
        if (!confirmedUniformOpt) {
            confirmedUniformOpt = true;
            DebugLog.General.println("[OptiZomb] GL_CHARS active: direct uniform calls bypassing HashMap lookups");
        }
    }

    public static void reportIfNeeded() {
        if (!OptiZombConfig.GL_CHARS || !OptiZombConfig.DIAGNOSTICS) return;
        long now = System.nanoTime();
        if (lastReportTime == 0) {
            lastReportTime = now;
            return;
        }
        long elapsed = now - lastReportTime;
        if (elapsed >= 5_000_000_000L) {
            double secs = elapsed / 1_000_000_000.0;
            int totalTex = texCacheHits + texCacheMisses;
            float hitRate = totalTex > 0 ? (100.0f * texCacheHits / totalTex) : 0.0f;
            DebugLog.General.println(String.format(
                "[OptiZomb] GL_CHARS stats (%.1fs): %d setTexture bypassed (%d bind-skip), %d uniform bypassed, %d lights (%d precomputed trig, %d early-out)",
                secs, totalTex, texCacheHits, uniformBypass, lightsProcessed, lightsProcessed > 0 ? lightsProcessed / 5 : 0, lightEarlyOuts));
            texCacheHits = 0;
            texCacheMisses = 0;
            lightEarlyOuts = 0;
            lightsProcessed = 0;
            uniformBypass = 0;
            lastReportTime = now;
        }
    }

    public static void beginCharacterState() {
        if (OptiZombConfig.GL_CHARS) {
            if (!confirmed) {
                confirmed = true;
                DebugLog.General.println("[OptiZomb] GL_CHARS active: client attrib eliminated, server attrib narrowed to 0x6500");
            }
            // No glPushClientAttrib - tracked by VertexBufferObject.restoreClientState()
            GL11.glPushAttrib(0x6500);       // GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT | GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT
            OptiZombRenderDebug.recordAttribPush();
            GL11.glEnable(3042);             // GL_BLEND
            GL11.glBlendFunc(770, 771);      // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
            GL11.glEnable(3008);             // GL_ALPHA_TEST
            GL11.glAlphaFunc(516, 0.01F);    // GL_GREATER, 0.01 (tighter than vanilla 0.0)
            GL11.glEnable(2929);             // GL_DEPTH_TEST
            GL11.glDepthFunc(513);           // GL_LESS - hoisted from per-DrawChar
            GL11.glDisable(3089);            // GL_SCISSOR_TEST off
            GL11.glEnable(2884);             // GL_CULL_FACE - hoisted from per-DrawChar
            GL11.glCullFace(1028);           // GL_BACK - hoisted from per-DrawChar
        } else {
            GL11.glPushClientAttrib(-1);
            GL11.glPushAttrib(1048575);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);
            GL11.glAlphaFunc(516, 0.0F);
            GL11.glEnable(2929);
            GL11.glDisable(3089);
        }
    }

    public static void endCharacterState() {
        if (OptiZombConfig.GL_CHARS) {
            Shader.resetLastBound();
            Shader.resetCharacterTextureCache();
            GL11.glPopAttrib();                      // restores DEPTH+STENCIL+ENABLE+COLOR_BUFFER
            OptiZombRenderDebug.recordAttribPop();
            VertexBufferObject.restoreClientState();  // replaces glPopClientAttrib
            VertexBufferObject.resetVBOCache();
            Texture.lastTextureID = -1;
            GL11.glEnable(3553);                     // GL_TEXTURE_2D
            SpriteRenderer.ringBuffer.restoreVBOs = true;
            GL11.glDisable(2929);                    // GL_DEPTH_TEST off
            GL11.glDisable(2884);                    // GL_CULL_FACE off (was enabled in setup)
            GL11.glEnable(3089);                     // GL_SCISSOR_TEST on (was disabled in setup)
            GL11.glEnable(3042);                     // GL_BLEND
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);                     // GL_ALPHA_TEST
            GL11.glAlphaFunc(516, 0.0F);
            reportIfNeeded();
        } else {
            GL11.glPopAttrib();
            GL11.glPopClientAttrib();
            Texture.lastTextureID = -1;
            GL11.glEnable(3553);
            SpriteRenderer.ringBuffer.restoreVBOs = true;
            GL11.glDisable(2929);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);
            GL11.glAlphaFunc(516, 0.0F);
        }
    }

    public static void beginOutlineState() {
        if (OptiZombConfig.GL_CHARS) {
            GL11.glPushClientAttrib(0x2);    // GL_CLIENT_VERTEX_ARRAY_BIT only
            GL11.glPushAttrib(0x46108);      // depth+color+enable+polygon+scissor
            OptiZombRenderDebug.recordClientAttribPush();
            OptiZombRenderDebug.recordAttribPush();
            GL11.glEnable(2929);
            GL11.glDisable(3089);
        } else {
            GL11.glPushClientAttrib(-1);
            GL11.glPushAttrib(1048575);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);
            GL11.glAlphaFunc(516, 0.0F);
            GL11.glEnable(2929);
            GL11.glDisable(3089);
        }
    }

    public static void endOutlineState() {
        GL11.glPopAttrib();
        GL11.glPopClientAttrib();
        OptiZombRenderDebug.recordAttribPop();
        OptiZombRenderDebug.recordClientAttribPop();
        if (OptiZombConfig.GL_CHARS) {
            Shader.resetCharacterTextureCache();
            VertexBufferObject.resetVBOCache();
        }
        Texture.lastTextureID = -1;
        SpriteRenderer.ringBuffer.restoreVBOs = true;
        if (!OptiZombConfig.GL_CHARS) {
            GL11.glDisable(2929);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);
            GL11.glAlphaFunc(516, 0.0F);
        }
    }

    public static void beginVehicleState() {
        if (OptiZombConfig.GL_CHARS) {
            GL11.glPushClientAttrib(0x2);
            GL11.glPushAttrib(0x46108);
            OptiZombRenderDebug.recordClientAttribPush();
            OptiZombRenderDebug.recordAttribPush();
        } else {
            GL11.glPushClientAttrib(-1);
            GL11.glPushAttrib(1048575);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3008);
            GL11.glAlphaFunc(516, 0.0F);
        }
    }

    public static void endVehicleState() {
        GL11.glPopAttrib();
        GL11.glPopClientAttrib();
        OptiZombRenderDebug.recordAttribPop();
        OptiZombRenderDebug.recordClientAttribPop();
        if (OptiZombConfig.GL_CHARS) {
            VertexBufferObject.resetVBOCache();
        }
        Texture.lastTextureID = -1;
        GL11.glEnable(3553);
        SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        SpriteRenderer.ringBuffer.restoreVBOs = true;
        GL11.glDisable(2929);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3008);
        GL11.glAlphaFunc(516, 0.0F);
    }
}

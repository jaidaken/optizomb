package zombie.optizomb;

import org.lwjgl.opengl.GL11;
import zombie.core.Core;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;
import zombie.debug.DebugLog;
import zombie.iso.sprite.SkyBox;
import zombie.core.skinnedmodel.model.VehicleModelInstance;

public final class OptiZombGLFix {

    public static final boolean GL_FIX = OptiZombConfig.GL_FIX;

    private static boolean confirmedErrorThrow = false;
    private static boolean confirmedErrorCheck = false;
    private static boolean confirmedVehicleTex = false;

    private static int skippedErrorThrow = 0;
    private static int skippedErrorCheck = 0;
    private static int skippedTexEnvi = 0;
    private static int skippedGetError = 0;
    private static long lastReportTime = 0;

    public static void onSkipErrorThrow() {
        skippedErrorThrow++;
        if (!confirmedErrorThrow) {
            confirmedErrorThrow = true;
            DebugLog.General.println("[OptiZomb] GL_FIX active: checkGLErrorThrow bypassed");
        }
    }

    public static void onSkipErrorCheck() {
        skippedErrorCheck++;
        if (!confirmedErrorCheck) {
            confirmedErrorCheck = true;
            DebugLog.General.println("[OptiZomb] GL_FIX active: checkGLError bypassed");
        }
    }

    public static void reportIfNeeded() {
        if (!OptiZombConfig.GL_FIX || !OptiZombConfig.DIAGNOSTICS) return;
        long now = System.nanoTime();
        if (lastReportTime == 0) {
            lastReportTime = now;
            return;
        }
        long elapsed = now - lastReportTime;
        if (elapsed >= 5_000_000_000L) {
            double secs = elapsed / 1_000_000_000.0;
            DebugLog.General.println(String.format(
                "[OptiZomb] GL_FIX stats (%.1fs): skipped %d glGetError (throw), %d glGetError (check), %d glTexEnvi, %d glGetError (reflection)",
                secs, skippedErrorThrow, skippedErrorCheck, skippedTexEnvi, skippedGetError));
            skippedErrorThrow = 0;
            skippedErrorCheck = 0;
            skippedTexEnvi = 0;
            skippedGetError = 0;
            lastReportTime = now;
        }
    }

    public static void setupVehicleTextures(Shader shader, VehicleModelInstance vmi) {
        if (OptiZombConfig.GL_FIX) {
            if (!confirmedVehicleTex) {
                confirmedVehicleTex = true;
                DebugLog.General.println("[OptiZomb] GL_FIX active: vehicle texture optimization applied");
            }
            // glTexEnvi must be called per texture unit (it applies to the ACTIVE unit)
            shader.setTexture(vmi.tex, "Texture0", 0);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureRust, "TextureRust", 1);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureMask, "TextureMask", 2);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureLights, "TextureLights", 3);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage1Overlay, "TextureDamage1Overlay", 4);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage1Shell, "TextureDamage1Shell", 5);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage2Overlay, "TextureDamage2Overlay", 6);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage2Shell, "TextureDamage2Shell", 7);
            GL11.glTexEnvi(8960, 8704, 7681);
        } else {
            shader.setTexture(vmi.tex, "Texture0", 0);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureRust, "TextureRust", 1);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureMask, "TextureMask", 2);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureLights, "TextureLights", 3);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage1Overlay, "TextureDamage1Overlay", 4);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage1Shell, "TextureDamage1Shell", 5);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage2Overlay, "TextureDamage2Overlay", 6);
            GL11.glTexEnvi(8960, 8704, 7681);
            shader.setTexture(vmi.textureDamage2Shell, "TextureDamage2Shell", 7);
            GL11.glTexEnvi(8960, 8704, 7681);
        }

        try {
            if (Core.getInstance().getPerfReflectionsOnLoad()) {
                shader.setTexture((Texture)SkyBox.getInstance().getTextureCurrent(), "TextureReflectionA", 8);
                if (!OptiZombConfig.GL_FIX) {
                    GL11.glTexEnvi(8960, 8704, 7681);
                    GL11.glGetError();
                } else {
                    skippedTexEnvi++;
                    skippedGetError++;
                }
            }
        } catch (Throwable throwable0) {
        }

        try {
            if (Core.getInstance().getPerfReflectionsOnLoad()) {
                shader.setTexture((Texture)SkyBox.getInstance().getTexturePrev(), "TextureReflectionB", 9);
                if (!OptiZombConfig.GL_FIX) {
                    GL11.glTexEnvi(8960, 8704, 7681);
                    GL11.glGetError();
                } else {
                    skippedTexEnvi++;
                    skippedGetError++;
                }
            }
        } catch (Throwable throwable1) {
        }

        shader.setReflectionParam(SkyBox.getInstance().getTextureShift(), vmi.refWindows, vmi.refBody);
        shader.setTextureUninstall1(vmi.textureUninstall1);
        shader.setTextureUninstall2(vmi.textureUninstall2);
        shader.setTextureLightsEnables1(vmi.textureLightsEnables1);
        shader.setTextureLightsEnables2(vmi.textureLightsEnables2);
        shader.setTextureDamage1Enables1(vmi.textureDamage1Enables1);
        shader.setTextureDamage1Enables2(vmi.textureDamage1Enables2);
        shader.setTextureDamage2Enables1(vmi.textureDamage2Enables1);
        shader.setTextureDamage2Enables2(vmi.textureDamage2Enables2);
        shader.setMatrixBlood1(vmi.matrixBlood1Enables1, vmi.matrixBlood1Enables2);
        shader.setMatrixBlood2(vmi.matrixBlood2Enables1, vmi.matrixBlood2Enables2);
        shader.setTextureRustA(vmi.textureRustA);
    }
}

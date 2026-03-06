package zombie.optizomb;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjglx.BufferUtils;
import zombie.debug.DebugLog;

/**
 * GPU-side bone matrix upload via Shader Storage Buffer Object (SSBO).
 *
 * Replaces per-zombie glUniformMatrix4fv (3840 bytes each) with a single bulk upload.
 * All zombie bone matrices are packed into one GPU buffer per frame.
 * The shader reads bones via SSBO buffer block (layout std430, binding 0).
 *
 * Uses a plain float[] array for CPU-side staging to avoid FloatBuffer view/limit issues.
 * Only converts to FloatBuffer at GPU upload time.
 */
public final class OptiZombBoneTBO {
    public static final OptiZombBoneTBO instance = new OptiZombBoneTBO();

    // Max capacity: 20480 model instances (4096 zombies * ~5 clothing layers each)
    // * 60 bones * 4 vec4s per bone = 4,915,200 vec4s
    // Each vec4 = 4 floats. Total floats = 19,660,800 (~75MB).
    private static final int MAX_ZOMBIES = 20480;
    private static final int MAX_BONES = 60;
    private static final int VEC4S_PER_BONE = 4;
    private static final int FLOATS_PER_VEC4 = 4;
    private static final int MAX_VEC4S = MAX_ZOMBIES * MAX_BONES * VEC4S_PER_BONE;
    private static final int MAX_FLOATS = MAX_VEC4S * FLOATS_PER_VEC4;

    // GL handle
    private int bufferID = -1;

    // CPU staging: plain float array (no NIO buffer state issues)
    private float[] stagingArray;

    // NIO buffer for GPU upload only
    private FloatBuffer uploadBuffer;

    // Frame state
    private int writeCursor;
    private int lastUploadedVec4;
    private boolean orphanedThisFrame;
    private boolean initialized;
    private boolean supported;

    private OptiZombBoneTBO() {}

    public void init() {
        if (initialized) return;

        try {
            supported = GL.getCapabilities().OpenGL43;
            if (!supported) {
                DebugLog.General.warn("[OptiZomb] BONE_TBO: GL 4.3 not available, falling back to uniform uploads");
                initialized = true;
                return;
            }

            bufferID = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferID);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)MAX_FLOATS * 4, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            stagingArray = new float[MAX_FLOATS];
            uploadBuffer = BufferUtils.createFloatBuffer(MAX_FLOATS);

            initialized = true;
            DebugLog.General.println("[OptiZomb] BONE_TBO: SSBO initialized, capacity=%d model instances, %.1f MB",
                MAX_ZOMBIES, (float)MAX_FLOATS * 4 / (1024 * 1024));
        } catch (Exception e) {
            DebugLog.General.error("[OptiZomb] BONE_TBO: init deferred (no GL context yet): " + e.getMessage());
        }
    }

    public void beginFrame() {
        if (!initialized || !supported) return;
        writeCursor = 0;
        lastUploadedVec4 = 0;
        orphanedThisFrame = false;
    }

    public void ensureInit() {
        if (!initialized) {
            init();
        }
    }

    public boolean isReady() {
        return supported && initialized;
    }

    /**
     * Append bone matrices to the staging array.
     *
     * @param matrixPalette FloatBuffer containing row-major mat4s (position at 0, limit set)
     * @param boneCount number of bones (mat4s) in the palette
     * @return vec4 offset (base index for SSBO lookup in shader), or -1 if buffer full
     */
    public int appendBoneData(FloatBuffer matrixPalette, int boneCount) {
        if (!supported) return -1;

        int vec4sNeeded = boneCount * VEC4S_PER_BONE;
        if (writeCursor + vec4sNeeded > MAX_VEC4S) {
            return -1;
        }

        int srcFloatsNeeded = boneCount * 16;
        if (matrixPalette.remaining() < srcFloatsNeeded) {
            return -1;
        }

        int baseVec4 = writeCursor;
        int dstIdx = writeCursor * FLOATS_PER_VEC4;

        // Store bone data as-is (row-major, same layout as Matrix4f.store()).
        // The shader transposes when reading - same as glUniformMatrix4fv(transpose=true).
        int srcPos = matrixPalette.position();
        matrixPalette.position(srcPos);
        matrixPalette.get(stagingArray, dstIdx, boneCount * 16);
        matrixPalette.position(srcPos);

        writeCursor += vec4sNeeded;

        OptiZombBoneTBOLog.onAppend(boneCount, baseVec4);
        return baseVec4;
    }

    /**
     * Upload any new staging data to GPU and bind SSBO to binding point 0.
     */
    public void uploadAndBind() {
        if (!supported) return;

        if (writeCursor > lastUploadedVec4) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferID);

            if (!orphanedThisFrame) {
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)MAX_FLOATS * 4, GL15.GL_STREAM_DRAW);
                orphanedThisFrame = true;
            }

            int startFloat = lastUploadedVec4 * FLOATS_PER_VEC4;
            int count = (writeCursor - lastUploadedVec4) * FLOATS_PER_VEC4;

            uploadBuffer.clear();
            uploadBuffer.put(stagingArray, startFloat, count);
            uploadBuffer.flip();
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (long)startFloat * 4, uploadBuffer);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            lastUploadedVec4 = writeCursor;

            OptiZombBoneTBOLog.onUpload(count);
        }

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, bufferID);
    }

    public void unbind() {
        if (!supported) return;
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    /** Expose write cursor for diagnostics */
    public int getWriteCursor() {
        return writeCursor;
    }
}

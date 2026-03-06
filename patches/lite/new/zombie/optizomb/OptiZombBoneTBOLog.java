package zombie.optizomb;

import zombie.debug.DebugLog;

/**
 * Diagnostic logger for BONE_TBO optimization.
 * Reports every 5 seconds when DIAGNOSTICS is enabled.
 */
public final class OptiZombBoneTBOLog {
    private static long lastReportTime;

    // Counters (reset every report interval)
    private static int appendCount;
    private static int totalBones;
    private static int uploadCount;
    private static int totalFloatsUploaded;
    private static int lateAppendCount;
    private static int uniformFallbackCount;

    // One-shot confirmations
    private static boolean confirmedSSBO;
    private static boolean confirmedShader;
    private static boolean confirmedUpload;

    private OptiZombBoneTBOLog() {}

    public static void onAppend(int boneCount, int baseVec4) {
        appendCount++;
        totalBones += boneCount;
    }

    public static void onUpload(int floatCount) {
        uploadCount++;
        totalFloatsUploaded += floatCount;
        if (!confirmedUpload) {
            confirmedUpload = true;
            DebugLog.General.println("[OptiZomb] BONE_TBO confirmed: GPU upload active (%d floats)", floatCount);
        }
    }

    public static void onLateAppend() {
        lateAppendCount++;
    }

    public static void onUniformFallback() {
        uniformFallbackCount++;
    }

    public static void confirmSSBO() {
        if (!confirmedSSBO) {
            confirmedSSBO = true;
            DebugLog.General.println("[OptiZomb] BONE_TBO confirmed: SSBO initialized and ready");
        }
    }

    public static void confirmShader(String shaderName) {
        if (!confirmedShader) {
            confirmedShader = true;
            DebugLog.General.println("[OptiZomb] BONE_TBO confirmed: TBO shader '%s' compiled and ready", shaderName);
        }
    }

    public static void reportIfNeeded() {
        if (!OptiZombConfig.DIAGNOSTICS) return;

        long now = System.currentTimeMillis();
        if (lastReportTime == 0) {
            lastReportTime = now;
            return;
        }

        if (now - lastReportTime >= 5000) {
            if (appendCount > 0 || uploadCount > 0) {
                float mbUploaded = (float)totalFloatsUploaded * 4 / (1024 * 1024);
                DebugLog.General.println(
                    "[OptiZomb] BONE_TBO (5s): appends=%d, bones=%d, uploads=%d, %.2f MB, lateAppends=%d, uniformFallbacks=%d",
                    appendCount, totalBones, uploadCount, mbUploaded, lateAppendCount, uniformFallbackCount
                );
            }

            appendCount = 0;
            totalBones = 0;
            uploadCount = 0;
            totalFloatsUploaded = 0;
            lateAppendCount = 0;
            uniformFallbackCount = 0;
            lastReportTime = now;
        }
    }
}

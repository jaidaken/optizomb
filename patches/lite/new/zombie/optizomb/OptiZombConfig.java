package zombie.optizomb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import zombie.debug.DebugLog;

/**
 * OptiZomb Lite - Runtime feature flags.
 *
 * All fields are static final so the JIT can eliminate disabled branches entirely.
 * Loads from optizomb.properties at class-load time.
 */
public final class OptiZombConfig {

    public static final String VERSION = "0.1.4";

    private static final Properties props = new Properties();
    private static final String configSource;

    static {
        String source = null;
        File f = new File(System.getProperty("user.dir"), "optizomb.properties");
        if (!f.exists()) {
            f = new File(new File(System.getProperty("user.dir")).getParent(), "optizomb.properties");
        }
        if (!f.exists()) {
            f = new File(System.getProperty("user.home"), ".optizomb/config.properties");
        }
        if (f.exists()) {
            source = f.getAbsolutePath();
            try (FileInputStream fis = new FileInputStream(f)) {
                props.load(fis);
            } catch (IOException e) {
                DebugLog.General.error("[OptiZomb] Failed to load " + f.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        configSource = source;
        // Always print to console so it's visible even before DebugLog is initialized
        if (source == null) {
            System.out.println("[OptiZomb] WARNING: No config file found - ALL optimizations ENABLED by default");
            System.out.println("[OptiZomb] Searched: " + System.getProperty("user.dir") + "/optizomb.properties");
        } else {
            System.out.println("[OptiZomb] Config loaded from: " + source);
        }
    }

    private static boolean resolve(String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return val.trim().equalsIgnoreCase("true") || val.trim().equals("1");
    }

    private static short resolveShort(String key, short defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Short.parseShort(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // --- Feature flags (dependency order) ---

    /** Group 1: GL error check removal + redundant GL call elimination */
    public static final boolean GL_FIX = resolve("opt.glfix", true);

    /** Group 2: Character rendering pipeline (requires GL_FIX) */
    public static final boolean GL_CHARS = resolve("opt.glchars", true) && GL_FIX;

    /** Group 3: Bone matrix TBO upload (requires GL_CHARS) */
    public static final boolean BONE_TBO = resolve("opt.bonetbo", true) && GL_CHARS;

    /** Group 4: Tiles + character render reorder */
    public static final boolean TILES_CHARS = resolve("opt.tileschars", true);

    /** Group 5: Sprite batch merge optimization */
    public static final boolean BATCH_MERGE = resolve("opt.batchmerge", true);

    /** Group 6: Floor tile rendering pipeline */
    public static final boolean FLOOR_PIPELINE = resolve("opt.floorpipeline", true);

    /** Group 7: Floor FBO caching (disabled: camera float jitter prevents cache hits) */
    public static final boolean FLOOR_FBO = resolve("opt.floorfbo", false);

    /** Group 9: Blood splat rendering optimization */
    public static final boolean BLOOD = resolve("opt.blood", true);

    /** Group 10: Shadow rendering optimization */
    public static final boolean SHADOWS = resolve("opt.shadows", true);

    /** Group 11: Item container type-index optimization */
    public static final boolean ITEMS = resolve("opt.items", true);

    /** Group 12: Diagnostics (ZombPerf logging) */
    public static final boolean DIAGNOSTICS = resolve("opt.diagnostics", true);

    /** Group 13: Terrain blend fix */
    public static final boolean TERRAIN = resolve("opt.terrain", true);

    /** Group 14: Zoom-level FBO optimization */
    public static final boolean ZOOM = resolve("opt.zoom", true);

    /** Scene culling + bone LOD */
    public static final boolean SCENE_CULL = resolve("opt.scenecull", true);
    public static final boolean BONE_LOD = resolve("opt.bonelod", true);

    /** Cutaway frame-stamp caching */
    public static final boolean CUTAWAY = resolve("opt.cutaway", true);

    /** StartFrame sub-phase profiling */
    public static final boolean STARTFRAME = resolve("opt.startframe", true);

    /** Fog rendering optimization */
    public static final boolean FOG = resolve("opt.fog", true);

    /** Separation throttle (skip separate() for distant zombies) */
    public static final boolean SEPARATE_THROTTLE = resolve("opt.separatethrottle", true);

    /** AI throttle (skip RespondToSound/updateSearchForCorpse for distant zombies) */
    public static final boolean AI_THROTTLE = resolve("opt.aithrottle", true);

    /** Zombie render cap (vanilla = 510, OptiZomb default = 4096) */
    public static final short ZOMBIE_RENDER_CAP = resolveShort("opt.zombiecap", (short)4096);

    /** Log resolved configuration to debug log */
    public static void logConfig() {
        if (configSource == null) {
            DebugLog.General.println("[OptiZomb] No config file found - all optimizations enabled by default");
        } else {
            DebugLog.General.println("[OptiZomb] Config loaded from: " + configSource);
        }
        DebugLog.General.println("[OptiZomb] v" + VERSION + " - Resolved feature flags:");
        DebugLog.General.println("[OptiZomb]   GL_FIX          = " + GL_FIX);
        DebugLog.General.println("[OptiZomb]   GL_CHARS         = " + GL_CHARS);
        DebugLog.General.println("[OptiZomb]   BONE_TBO         = " + BONE_TBO);
        DebugLog.General.println("[OptiZomb]   TILES_CHARS      = " + TILES_CHARS);
        DebugLog.General.println("[OptiZomb]   BATCH_MERGE      = " + BATCH_MERGE);
        DebugLog.General.println("[OptiZomb]   BLOOD            = " + BLOOD);
        DebugLog.General.println("[OptiZomb]   SHADOWS          = " + SHADOWS);
        DebugLog.General.println("[OptiZomb]   ITEMS            = " + ITEMS);
        DebugLog.General.println("[OptiZomb]   FLOOR_PIPELINE   = " + FLOOR_PIPELINE);
        DebugLog.General.println("[OptiZomb]   FLOOR_FBO        = " + FLOOR_FBO);
        DebugLog.General.println("[OptiZomb]   SCENE_CULL       = " + SCENE_CULL);
        DebugLog.General.println("[OptiZomb]   BONE_LOD         = " + BONE_LOD);
        DebugLog.General.println("[OptiZomb]   TERRAIN          = " + TERRAIN);
        DebugLog.General.println("[OptiZomb]   ZOOM             = " + ZOOM);
        DebugLog.General.println("[OptiZomb]   CUTAWAY          = " + CUTAWAY);
        DebugLog.General.println("[OptiZomb]   STARTFRAME       = " + STARTFRAME);
        DebugLog.General.println("[OptiZomb]   FOG              = " + FOG);
        DebugLog.General.println("[OptiZomb]   SEPARATE_THROTTLE= " + SEPARATE_THROTTLE);
        DebugLog.General.println("[OptiZomb]   AI_THROTTLE      = " + AI_THROTTLE);
        DebugLog.General.println("[OptiZomb]   DIAGNOSTICS      = " + DIAGNOSTICS);
        DebugLog.General.println("[OptiZomb]   ZOMBIE_RENDER_CAP= " + ZOMBIE_RENDER_CAP);
    }

    private OptiZombConfig() {}
}

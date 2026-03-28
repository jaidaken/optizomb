package zombie.optizomb;

import zombie.core.Core;
import zombie.debug.DebugLog;

/**
 * Render phase profiler using CPU-side nanoTime timestamps.
 * Measures wall-clock time spent in each rendering phase (CPU time issuing GL commands).
 * Reports averages every 5 seconds via [GPUPerf] log lines.
 *
 * Note: GL_TIMESTAMP queries are not usable because PZ's GL context is invisible
 * to LWJGL3's dispatch (glfwGetCurrentContext()==0, GL.setCapabilities() causes SIGSEGV).
 * CPU timing still reveals driver overhead, stalls, and relative phase costs.
 */
public final class OptiZombGPUProfiler {
    // Phase names - add new phases here
    public static final int PHASE_WORLD_RENDER = 0;
    public static final int PHASE_DEAD_BODY_ATLAS = 1;
    public static final int PHASE_WORLD_ITEM_ATLAS = 2;
    public static final int PHASE_CELL_RENDER = 3;
    public static final int PHASE_TILES_FLOOR = 4;
    public static final int PHASE_TILES_CHARS = 5;
    public static final int PHASE_TILES_ENV = 6;   // puddles, water, snow, blood, shading
    public static final int PHASE_VEHICLES = 7;
    public static final int PHASE_SKYBOX = 8;
    public static final int PHASE_UI = 9;
    public static final int PHASE_CHARACTERS = 10;   // performRenderCharacter (skinned model draw)
    public static final int PHASE_CHAR_OUTLINE = 11;  // performRenderCharacterOutline
    public static final int PHASE_WEATHER = 12;        // WeatherFxMask.renderFxMask
    public static final int PHASE_HEIGHT_TERRAIN = 13; // HeightTerrain.render (grass mesh)
    public static final int PHASE_DEBUG_LINES = 14;    // VBOLines.flush (debug line drawing)
    public static final int PHASE_WORLD_MAP = 15;      // WorldMapVBOs.drawElements
    public static final int PHASE_WORLD_MAP_LINES = 16; // VBOLinesUV.flush (world map lines)
    public static final int PHASE_OFFSCREEN = 17;       // Core.RenderOffScreenBuffer
    public static final int PHASE_ENDFRAME = 18;         // Core.EndFrame + EndFrameText + EndFrameUI
    public static final int PHASE_LUA_RENDER = 19;       // OnRenderTick + OnPostRender
    public static final int PHASE_PRE_UI = 20;           // TextManager.DrawTextFromGameWorld + SkyBox.draw
    public static final int PHASE_MODEL_OUTLINES = 21;   // ModelOutlines startFrameMain + endFrameMain
    public static final int PHASE_START_FRAME = 22;      // Core.StartFrame (viewport, FBO bind, clear)
    public static final int PHASE_WORLD_TEXT = 23;        // renderFrameText content (UI elements, markers, text batches)
    public static final int PHASE_POST_RENDER = 24;       // RenderSettings.legacyPostRender
    public static final int PHASE_WORLD_MISC = 25;        // cursor, debug renders, LineDrawer between cellRender and weather
    public static final int PHASE_WEATHER_INIT = 26;      // WeatherFxMask.initMask
    public static final int PHASE_CLIENT_MAP = 27;        // ClientServerMap + PassengerMap (multiplayer only)
    public static final int PHASE_START_FRAME_UI = 28;    // Core.StartFrameUI (FBO bind, offscreen UI setup)
    public static final int PHASE_START_FRAME_TEXT = 29;   // Core.StartFrameText (glDoStartFrame)
    public static final int PHASE_UI_MISC = 30;            // ZomboidRadio, atlas renderUI, sleep clock, ActiveMods, debug renders
    public static final int PHASE_DEBUG_CELL = 31;         // renderDebugPhysics + renderDebugLighting (debug only)
    public static final int PHASE_FLOOR_SHADING = 32;     // ShadingTexture GPU draw (texture upload + shader quad)
    public static final int PHASE_COUNT = 33;

    private static final String[] PHASE_NAMES = {
        "worldRender", "deadBodyAtlas", "worldItemAtlas", "cellRender",
        "tilesFloor", "tilesChars", "tilesEnv", "vehicles",
        "skybox", "ui", "characters", "charOutline", "weather",
        "heightTerrain", "debugLines", "worldMap", "worldMapLines",
        "offscreen", "endFrame", "luaRender", "preUI", "modelOutlines",
        "startFrame", "worldText", "postRender", "worldMisc", "weatherInit", "clientMap",
        "startFrameUI", "startFrameText", "uiMisc", "debugCell", "floorShading"
    };

    // Per-phase start timestamp (nanoTime)
    private static final long[] phaseStartNs = new long[PHASE_COUNT];

    // Accumulation for averaging
    private static final long[] phaseAccumNs = new long[PHASE_COUNT];
    private static final int[] phaseValidCount = new int[PHASE_COUNT];
    private static int frameCount = 0;
    private static long lastReportTime = 0;
    private static final int REPORT_INTERVAL_MS = 5000;

    private static boolean enabled = false;
    private static boolean initialized = false;

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    /** Call at start of frame. Only active in debug mode. */
    public static void beginFrame() {
        if (!Core.bDebug) { enabled = false; return; }
        enabled = true;
        if (!initialized) {
            initialized = true;
            lastReportTime = System.currentTimeMillis();
            DebugLog.General.warn("[GPUPerf] Initialized CPU-side render profiler with " + PHASE_COUNT + " phases");
        }

        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastReportTime >= REPORT_INTERVAL_MS && frameCount > 0) {
            reportAndReset(now);
        }
    }

    /** Record a timestamp at the START of a phase. */
    public static void beginPhase(int phase) {
        if (!enabled || !initialized) return;
        phaseStartNs[phase] = System.nanoTime();
    }

    /** Record a timestamp at the END of a phase. */
    public static void endPhase(int phase) {
        if (!enabled || !initialized) return;
        long start = phaseStartNs[phase];
        if (start != 0) {
            long elapsed = System.nanoTime() - start;
            phaseAccumNs[phase] += elapsed;
            phaseValidCount[phase]++;
            phaseStartNs[phase] = 0;
        }
    }

    private static void reportAndReset(long now) {
        // Compute per-phase averages
        double[] avg = new double[PHASE_COUNT];
        for (int i = 0; i < PHASE_COUNT; i++) {
            avg[i] = phaseValidCount[i] > 0
                ? (phaseAccumNs[i] / (double) phaseValidCount[i]) / 1_000_000.0
                : 0;
            phaseAccumNs[i] = 0;
            phaseValidCount[i] = 0;
        }

        // Shorthand aliases
        double startFrame     = avg[PHASE_START_FRAME];
        double modelOutlines  = avg[PHASE_MODEL_OUTLINES];
        double worldRender    = avg[PHASE_WORLD_RENDER];
        double weatherInit    = avg[PHASE_WEATHER_INIT];
        double deadBodyAtlas  = avg[PHASE_DEAD_BODY_ATLAS];
        double worldItemAtlas = avg[PHASE_WORLD_ITEM_ATLAS];
        double cellRender     = avg[PHASE_CELL_RENDER];
        double tilesFloor     = avg[PHASE_TILES_FLOOR];
        double tilesEnv       = avg[PHASE_TILES_ENV];
        double tilesChars     = avg[PHASE_TILES_CHARS];
        double debugCell      = avg[PHASE_DEBUG_CELL];
        double worldMisc      = avg[PHASE_WORLD_MISC];
        double weather        = avg[PHASE_WEATHER];
        double clientMap      = avg[PHASE_CLIENT_MAP];
        double skybox         = avg[PHASE_SKYBOX];
        double postRender     = avg[PHASE_POST_RENDER];
        double luaRender      = avg[PHASE_LUA_RENDER];
        double endFrame       = avg[PHASE_ENDFRAME];
        double offscreen      = avg[PHASE_OFFSCREEN];
        double startFrameText = avg[PHASE_START_FRAME_TEXT];
        double worldText      = avg[PHASE_WORLD_TEXT];
        double startFrameUI   = avg[PHASE_START_FRAME_UI];
        double preUI          = avg[PHASE_PRE_UI];
        double ui             = avg[PHASE_UI];
        double uiMisc         = avg[PHASE_UI_MISC];
        double characters     = avg[PHASE_CHARACTERS];
        double charOutline    = avg[PHASE_CHAR_OUTLINE];
        double vehicles       = avg[PHASE_VEHICLES];
        double heightTerrain  = avg[PHASE_HEIGHT_TERRAIN];
        double debugLines     = avg[PHASE_DEBUG_LINES];
        double worldMap       = avg[PHASE_WORLD_MAP];
        double worldMapLines  = avg[PHASE_WORLD_MAP_LINES];
        double floorShading   = avg[PHASE_FLOOR_SHADING];

        // Residuals (envelope minus children)
        double cellOther = cellRender - tilesFloor - tilesEnv - tilesChars - debugCell;
        double worldOther = worldRender - weatherInit - deadBodyAtlas - worldItemAtlas
            - cellRender - worldMisc - weather - clientMap - skybox;

        // Grand total of all leaf phases
        double total = startFrame + modelOutlines + worldRender + postRender + luaRender
            + endFrame + offscreen + startFrameText + worldText + startFrameUI + preUI
            + ui + uiMisc + characters + charOutline + vehicles + heightTerrain
            + debugLines + worldMap + worldMapLines;

        // Build tree report
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[GPUPerf] %d frames, TOTAL=%.1fms", frameCount, total));

        // -- renderFrame block --
        line(sb, 1, "startFrame",     startFrame);
        line(sb, 1, "modelOutlines",  modelOutlines);
        line(sb, 1, "worldRender",    worldRender);
        line(sb, 2, "weatherInit",    weatherInit);
        line(sb, 2, "deadBodyAtlas",  deadBodyAtlas);
        line(sb, 2, "worldItemAtlas", worldItemAtlas);
        line(sb, 2, "cellRender",     cellRender);
        line(sb, 3, "tilesFloor",     tilesFloor);
        line(sb, 3, "tilesEnv",       tilesEnv);
        line(sb, 4, "floorShading",  floorShading);
        line(sb, 3, "tilesChars",     tilesChars);
        line(sb, 3, "debugCell",      debugCell);
        if (cellOther > 0.01) line(sb, 3, "(cellOther)", cellOther);
        // Sub-phases that fire from deep call sites within cellRender
        line(sb, 3, "characters",     characters);
        line(sb, 3, "charOutline",    charOutline);
        line(sb, 3, "heightTerrain",  heightTerrain);
        line(sb, 3, "vehicles",       vehicles);
        line(sb, 2, "worldMisc",      worldMisc);
        line(sb, 3, "debugLines",     debugLines);
        line(sb, 2, "weather",        weather);
        line(sb, 2, "clientMap",      clientMap);
        line(sb, 2, "skybox",         skybox);
        if (worldOther > 0.01) line(sb, 2, "(worldOther)", worldOther);
        line(sb, 1, "postRender",     postRender);
        line(sb, 1, "luaRender",      luaRender);

        // -- offscreen --
        line(sb, 1, "offscreen",      offscreen);

        // -- renderFrameText block --
        line(sb, 1, "startFrameText", startFrameText);
        line(sb, 1, "worldText",      worldText);

        // -- renderFrameUI block --
        line(sb, 1, "startFrameUI",   startFrameUI);
        line(sb, 1, "preUI",          preUI);
        line(sb, 1, "ui",             ui);
        line(sb, 1, "uiMisc",         uiMisc);

        // -- endFrame (accumulated across all 3 EndFrame calls) --
        line(sb, 1, "endFrame",       endFrame);

        // -- world map (separate screen) --
        line(sb, 1, "worldMap",       worldMap);
        line(sb, 1, "worldMapLines",  worldMapLines);

        DebugLog.General.warn(sb.toString());
        frameCount = 0;
        lastReportTime = now;
    }

    private static void line(StringBuilder sb, int depth, String name, double ms) {
        if (ms < 0.01) return;
        sb.append("\n[GPUPerf]   ");
        for (int i = 0; i < depth; i++) sb.append("  ");
        if (depth >= 2) sb.append("|- ");
        sb.append(name).append(" = ").append(String.format("%.2f", ms)).append("ms");
    }
}

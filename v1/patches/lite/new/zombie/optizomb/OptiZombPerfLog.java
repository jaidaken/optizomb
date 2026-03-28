package zombie.optizomb;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import zombie.core.Core;
import zombie.debug.DebugLog;

/**
 * Lightweight per-frame timing tracker for zombie update optimizations.
 * Thread-safe via atomics for cross-thread accumulation from parallel workers.
 * Logs a summary every 5 seconds to the debug console.
 */
public final class OptiZombPerfLog {
    private static final long LOG_INTERVAL_NS = 5_000_000_000L; // 5 seconds

    // Atomic accumulators - safe for parallel worker threads
    private static final AtomicLong separateTimeNs = new AtomicLong();
    private static final AtomicLong updateTimeNs = new AtomicLong();
    private static final AtomicLong postupdateTimeNs = new AtomicLong();
    private static final AtomicLong drainTimeNs = new AtomicLong();

    private static final AtomicInteger separateCallCount = new AtomicInteger();
    private static final AtomicInteger separateSkipCount = new AtomicInteger();
    private static final AtomicInteger separateThrottleCount = new AtomicInteger();
    private static final AtomicInteger visibilitySkipCount = new AtomicInteger();
    private static final AtomicInteger visibilityThrottleCount = new AtomicInteger();
    private static final AtomicInteger animThrottleCount = new AtomicInteger();
    private static final AtomicInteger aiThrottleCount = new AtomicInteger();
    private static final AtomicInteger deferredEventCount = new AtomicInteger();
    private static final AtomicInteger drainedEventCount = new AtomicInteger();

    private static final AtomicLong renderTimeNs = new AtomicLong();
    private static final AtomicInteger renderedZombieCount = new AtomicInteger();
    private static final AtomicInteger sceneCulledZombieCount = new AtomicInteger();
    private static final AtomicInteger lightUpdateCount = new AtomicInteger();
    private static final AtomicInteger lightThrottleCount = new AtomicInteger();
    private static final AtomicLong bonePrecomputeTimeNs = new AtomicLong();
    private static final AtomicInteger boneSkipCount = new AtomicInteger();
    private static final AtomicLong postCollisionTimeNs = new AtomicLong();
    private static final AtomicLong postAnimTimeNs = new AtomicLong();
    private static final AtomicLong postLuaDrainTimeNs = new AtomicLong();
    private static final AtomicInteger postAnimCountTotal = new AtomicInteger();

    // Broad frame phase timers (main-thread only - no atomics needed)
    private static long frameStepTimeNs = 0;
    private static long logicTimeNs = 0;
    private static long worldUpdateTimeNs = 0;
    private static long cellUpdateTimeNs = 0;
    private static long updateStuffTimeNs = 0;
    private static long luaOnTickTimeNs = 0;
    private static long statesRenderTimeNs = 0;
    private static long cellRenderTimeNs = 0;
    private static long lightingTimeNs = 0;
    private static long doFrameReadyTimeNs = 0;
    private static long worldSimTimeNs = 0;
    private static long collisionMgrTimeNs = 0;
    private static long sceneCullTimeNs = 0;

    // CellRender sub-phase timers (main-thread only, accumulated across Z layers)
    private static long cellRenGridStacksTimeNs = 0;
    private static long cellRenCutawayTimeNs = 0;
    private static long cellRenFloorTimeNs = 0;
    private static long cellRenFloorShadingTimeNs = 0;
    private static long cellRenBuildingShadowTimeNs = 0;
    private static long cellRenEnvironmentTimeNs = 0;
    private static long cellRenVegCorpsesTimeNs = 0;
    private static long cellRenCharactersTimeNs = 0;
    private static long cellRenRenderLastTimeNs = 0;

    // Render sub-phase timers (main-thread only)
    private static long renderFrameTimeNs = 0;      // IngameState.renderframe()
    private static long renderFrameTextTimeNs = 0;   // IngameState.renderframetext()
    private static long renderFrameUITimeNs = 0;     // IngameState.renderframeui()
    private static long modelOutlinesTimeNs = 0;     // ModelOutlines start+end
    private static long isoWorldRenderTimeNs = 0;    // IsoWorld.render() total
    private static long coreEndFrameTimeNs = 0;      // Core.EndFrame + EndFrameText + EndFrameUI (legacy total)
    private static long offscreenBufferTimeNs = 0;   // RenderOffScreenBuffer
    private static long lightingJNITimeNs = 0;       // LightingJNI.DoLightingUpdateNew (non-threaded)
    private static long objectPickerTimeNs = 0;      // IsoObjectPicker.StartRender
    private static long uiManagerRenderTimeNs = 0;   // UIManager.render()
    private static long worldRenderMiscTimeNs = 0;   // PolygonalMap2 + Weather + SkyBox etc in IsoWorld
    private static long renderPreCellTimeNs = 0;     // WeatherFxMask.initMask + DeadBodyAtlas + WorldItemAtlas

    // Logic sub-phase timers (main-thread only)
    private static long logicNetworkTimeNs = 0;
    private static long logicInputTimeNs = 0;
    private static long logicUIUpdateTimeNs = 0;
    private static long logicStatesUpdateTimeNs = 0;

    // WorldUpdate sub-phase timers (main-thread only)
    private static long worldUpdPreCellTimeNs = 0;
    private static long worldUpdPostCollTimeNs = 0;

    // WorldSim sub-phase timers (main-thread only)
    private static long worldSimBulletTimeNs = 0;
    private static long worldSimVehicleTimeNs = 0;
    private static long worldSimObjectTimeNs = 0;

    // CellUpdate sub-phase timers (main-thread only)
    private static long cellUpdChunkMapTimeNs = 0;
    private static long cellUpdProcessObjTimeNs = 0;
    private static long cellUpdMiscTimeNs = 0;

    // CollisionMgr sub-phase timers (main-thread only)
    private static long collPushableTimeNs = 0;
    private static long collContactSolveTimeNs = 0;
    private static long collPostUpdateTimeNs = 0;
    private static long collSoundMgrTimeNs = 0;

    // Animation sub-phase timers (parallel workers - need atomics)
    private static final AtomicLong animAdvAnimatorTimeNs = new AtomicLong();
    private static final AtomicLong animModelSlotUpdTimeNs = new AtomicLong();
    private static final AtomicLong animLightInfoTimeNs = new AtomicLong();

    // --- NEW: CellRender detail timers (main-thread only) ---

    // gridStacks split
    private static long cellRenRecalcGridTimeNs = 0;
    private static long cellRenFlattenFoliageTimeNs = 0;

    // sprite batcher stats
    private static long spriteBatchCount = 0;
    private static long spriteLegacyCount = 0;
    private static long spriteBatchDrawCalls = 0;

    // characters split
    private static long cellRenMinusFloorTimeNs = 0;
    private static long cellRenDeferredCharsTimeNs = 0;
    private static long cellRenRenderCharsTimeNs = 0;
    private static long cellRenFogTimeNs = 0;

    // env split
    private static long cellRenPuddlesTimeNs = 0;
    private static long cellRenWaterTimeNs = 0;
    private static long cellRenSnowTimeNs = 0;
    private static long cellRenBloodTimeNs = 0;
    private static long cellRenShadowsTimeNs = 0;
    private static long cellRenMarkersTimeNs = 0;

    // --- NEW: EndFrame detail timers (main-thread only) ---
    private static long endFrameMainTimeNs = 0;
    private static long endFrameTextTimeNs = 0;
    private static long endFrameUITimeNs = 0;

    // --- NEW: ProcessObj detail timers (main-thread only) ---
    private static long cellUpdProcessItemsTimeNs = 0;
    private static long cellUpdProcessIsoObjTimeNs = 0;
    private static long cellUpdProcessObjectsTimeNs = 0;
    private static long cellUpdNetworkSendTimeNs = 0;

    // --- Item count tracking ---
    private static long totalProcessItemsCount = 0;
    private static long totalProcessWorldItemsCount = 0;
    private static long totalProcessIsoObjCount = 0;

    // --- NEW: UpdateStuff detail timers (main-thread only) ---
    private static long updStuffSoundTimeNs = 0;
    private static long updStuffFireTimeNs = 0;
    private static long updStuffRainTimeNs = 0;
    private static long updStuffVirtualZombTimeNs = 0;
    private static long updStuffPathfindTimeNs = 0;
    private static long updStuffPopTimeNs = 0;
    private static long updStuffMiscTimeNs = 0;

    // --- NEW: WorldRender misc detail timers (main-thread only) ---
    private static long worldRenCursorTimeNs = 0;
    private static long worldRenPolyMapTimeNs = 0;
    private static long worldRenDebugTimeNs = 0;

    // --- NEW: Pre-cell detail timers (main-thread only) ---
    private static long preCellWeatherInitTimeNs = 0;
    private static long preCellDeadBodyTimeNs = 0;
    private static long preCellWorldItemTimeNs = 0;

    // --- NEW: Other detail timers (main-thread only) ---
    private static long radioUpdateTimeNs = 0;
    private static long modelMgrUpdateTimeNs = 0;

    // --- NEW: Logic detail timers (main-thread only) ---
    private static long logicSteamTimeNs = 0;
    private static long logicSoundMgrTimeNs = 0;

    // --- NEW: StartFrame overhead timers (main-thread only) ---
    private static long startFrameTimeNs = 0;
    private static long startFrameTextTimeNs = 0;
    private static long startFrameUITimeNs = 0;
    private static long uiResizeTimeNs = 0;

    // --- NEW: CellRen remaining gap timers (main-thread only) ---
    private static long cellRenLuaPostFloorTimeNs = 0;
    private static long cellRenIsoMarkersTimeNs = 0;
    private static long cellRenImprovedFogTimeNs = 0;
    private static long cellRenLoopOverheadTimeNs = 0;

    // --- NEW: Remaining gap timers (main-thread only) ---
    private static long renFramePostRenderTimeNs = 0;    // legacyPostRender + OnPostRender + LineDrawer.clear
    private static long renTextContentTimeNs = 0;        // UIElement loop + markers + text batches
    private static long renUIMiscTimeNs = 0;             // ZomboidRadio + atlas + sleep + mods between uiMgr and EndFrameUI
    private static long updInternalChunkMapTimeNs = 0;   // chunk processing before worldUpdate
    private static long updInternalPostTickTimeNs = 0;   // ItemTransactionManager + MPStatistics
    private static long worldUpdMiscTimeNs = 0;          // IsoRegions + HaloTextHelper between cellUpdate and collMgr

    // --- NEW: Final gap-closure timers (main-thread only) ---
    private static long onRenderTickTimeNs = 0;          // LuaEventManager OnRenderTick between statesRender and frameReady
    private static long logicPreStatesTimeNs = 0;        // MapCollisionData.updateGameState between uiUpdate and statesUpdate
    private static long logicPostStatesTimeNs = 0;       // UIManager.resize + fileSystem + screenshot after statesUpdate
    private static long worldUpdVehicleTimeNs = 0;       // VehicleManager.serverUpdate (server-only, before worldSim)
    private static long updStuffPreTimedTimeNs = 0;      // GameTime + ScriptManager before first sub-timer
    private static long updStuffMetaTimeNs = 0;          // Meta.instance.update between rain and virtualZomb
    private static long updStuffMapCollTimeNs = 0;       // MapCollisionData.updateMain between virtualZomb and pop
    private static long renUIPreUIMgrTimeNs = 0;         // TextManager + SkyBox before uiMgr timer
    private static long cellRenPostTilesTimeNs = 0;      // IsoTree + DoBuilding + renderRain after renderLast
    private static long cellRenSetupTimeNs = 0;          // renderInternal setup: occlusion, stencil, roof hiding before RenderTiles

    // UI frame counters (main-thread only)
    private static int uiRenderFrameCount = 0;
    private static int uiSkipFrameCount = 0;

    // Render batch count (render-thread only - no atomics needed)
    private static int batchCount = 0;
    private static int batchCallCount = 0;

    // Main-thread only (no need for atomics)
    private static int frameCount = 0;
    private static int zombieCount = 0;
    private static long lastLogTime = System.nanoTime();

    /** Returns nanoTime for pairing with endSeparate(). */
    public static long beginSeparate() {
        return System.nanoTime();
    }

    /** Call at end of separate() with the value returned by beginSeparate(). */
    public static void endSeparate(long startNs) {
        separateTimeNs.addAndGet(System.nanoTime() - startNs);
        separateCallCount.incrementAndGet();
    }

    /** Call when separate() early-outs (distance skip). */
    public static void recordSeparateSkip() {
        separateCallCount.incrementAndGet();
        separateSkipCount.incrementAndGet();
    }

    /** Call when separate() is throttled (mid-distance frame skip). */
    public static void recordSeparateThrottle() {
        separateCallCount.incrementAndGet();
        separateThrottleCount.incrementAndGet();
    }

    /** Record time for the update() phase (called from main thread). */
    public static void recordUpdateTime(long elapsedNs) {
        updateTimeNs.addAndGet(elapsedNs);
    }

    /** Record time for the postupdate() phase (called from main thread). */
    public static void recordPostupdateTime(long elapsedNs) {
        postupdateTimeNs.addAndGet(elapsedNs);
    }

    /** Record deferred event drain time and count (called from main thread). */
    public static void recordDrainTime(long elapsedNs, int count) {
        drainTimeNs.addAndGet(elapsedNs);
        drainedEventCount.addAndGet(count);
    }

    public static void recordVisibilitySkip() {
        visibilitySkipCount.incrementAndGet();
    }

    public static void recordVisibilityThrottle() {
        visibilityThrottleCount.incrementAndGet();
    }

    public static void recordAnimThrottle() {
        animThrottleCount.incrementAndGet();
    }

    public static void recordAiThrottle() {
        aiThrottleCount.incrementAndGet();
    }

    /** Record render phase time (called from main/render thread). */
    public static void recordRenderTime(long elapsedNs) {
        renderTimeNs.addAndGet(elapsedNs);
    }

    /** Set the number of zombies actually rendered this frame. */
    public static void setRenderedZombieCount(int count) {
        renderedZombieCount.set(count);
    }

    public static void setSceneCulledZombieCount(int count) {
        sceneCulledZombieCount.set(count);
    }

    public static void recordDeferredEvent() {
        deferredEventCount.incrementAndGet();
    }

    public static void recordLightUpdate() {
        lightUpdateCount.incrementAndGet();
    }

    public static void recordLightThrottle() {
        lightThrottleCount.incrementAndGet();
    }

    public static void recordBoneSkip() {
        boneSkipCount.incrementAndGet();
    }

    public static void recordBonePrecomputeTime(long elapsedNs) {
        bonePrecomputeTimeNs.addAndGet(elapsedNs);
    }

    public static void recordPostCollisionTime(long elapsedNs) {
        postCollisionTimeNs.addAndGet(elapsedNs);
    }

    public static void recordPostAnimTime(long elapsedNs) {
        postAnimTimeNs.addAndGet(elapsedNs);
    }

    public static void recordPostLuaDrainTime(long elapsedNs) {
        postLuaDrainTimeNs.addAndGet(elapsedNs);
    }

    public static void recordPostAnimCount(int count) {
        postAnimCountTotal.addAndGet(count);
    }

    // --- Broad frame phase recorders (main-thread only) ---

    public static void recordFrameStepTime(long elapsedNs) {
        frameStepTimeNs += elapsedNs;
    }

    public static void recordLogicTime(long elapsedNs) {
        logicTimeNs += elapsedNs;
    }

    public static void recordWorldUpdateTime(long elapsedNs) {
        worldUpdateTimeNs += elapsedNs;
    }

    public static void recordCellUpdateTime(long elapsedNs) {
        cellUpdateTimeNs += elapsedNs;
    }

    public static void recordUpdateStuffTime(long elapsedNs) {
        updateStuffTimeNs += elapsedNs;
    }

    public static void recordLuaOnTickTime(long elapsedNs) {
        luaOnTickTimeNs += elapsedNs;
    }

    public static void recordStatesRenderTime(long elapsedNs) {
        statesRenderTimeNs += elapsedNs;
    }

    public static void recordCellRenderTime(long elapsedNs) {
        cellRenderTimeNs += elapsedNs;
    }

    public static void recordLightingTime(long elapsedNs) {
        lightingTimeNs += elapsedNs;
    }

    public static void recordDoFrameReadyTime(long elapsedNs) {
        doFrameReadyTimeNs += elapsedNs;
    }

    public static void recordWorldSimTime(long elapsedNs) {
        worldSimTimeNs += elapsedNs;
    }

    public static void recordCollisionMgrTime(long elapsedNs) {
        collisionMgrTimeNs += elapsedNs;
    }

    public static void recordSceneCullTime(long elapsedNs) {
        sceneCullTimeNs += elapsedNs;
    }

    // --- Render sub-phase recorders (main-thread only) ---

    public static void recordRenderFrameTime(long elapsedNs) {
        renderFrameTimeNs += elapsedNs;
    }

    public static void recordRenderFrameTextTime(long elapsedNs) {
        renderFrameTextTimeNs += elapsedNs;
    }

    public static void recordRenderFrameUITime(long elapsedNs) {
        renderFrameUITimeNs += elapsedNs;
    }

    public static void recordModelOutlinesTime(long elapsedNs) {
        modelOutlinesTimeNs += elapsedNs;
    }

    public static void recordIsoWorldRenderTime(long elapsedNs) {
        isoWorldRenderTimeNs += elapsedNs;
    }

    public static void recordCoreEndFrameTime(long elapsedNs) {
        coreEndFrameTimeNs += elapsedNs;
    }

    public static void recordOffscreenBufferTime(long elapsedNs) {
        offscreenBufferTimeNs += elapsedNs;
    }

    public static void recordLightingJNITime(long elapsedNs) {
        lightingJNITimeNs += elapsedNs;
    }

    public static void recordObjectPickerTime(long elapsedNs) {
        objectPickerTimeNs += elapsedNs;
    }

    public static void recordUIManagerRenderTime(long elapsedNs) {
        uiManagerRenderTimeNs += elapsedNs;
    }

    public static void recordWorldRenderMiscTime(long elapsedNs) {
        worldRenderMiscTimeNs += elapsedNs;
    }

    // --- CellRender sub-phase recorders (main-thread only) ---

    public static void recordCellRenGridStacksTime(long elapsedNs) {
        cellRenGridStacksTimeNs += elapsedNs;
    }

    public static void recordCellRenCutawayTime(long elapsedNs) {
        cellRenCutawayTimeNs += elapsedNs;
    }

    public static void recordCellRenFloorTime(long elapsedNs) {
        cellRenFloorTimeNs += elapsedNs;
    }

    public static void recordCellRenFloorShadingTime(long elapsedNs) {
        cellRenFloorShadingTimeNs += elapsedNs;
    }

    public static void recordCellRenBuildingShadowTime(long elapsedNs) {
        cellRenBuildingShadowTimeNs += elapsedNs;
    }

    public static void recordCellRenEnvironmentTime(long elapsedNs) {
        cellRenEnvironmentTimeNs += elapsedNs;
    }

    public static void recordCellRenVegCorpsesTime(long elapsedNs) {
        cellRenVegCorpsesTimeNs += elapsedNs;
    }

    public static void recordCellRenCharactersTime(long elapsedNs) {
        cellRenCharactersTimeNs += elapsedNs;
    }

    public static void recordCellRenRenderLastTime(long elapsedNs) {
        cellRenRenderLastTimeNs += elapsedNs;
    }

    public static void recordRenderPreCellTime(long elapsedNs) {
        renderPreCellTimeNs += elapsedNs;
    }

    // --- Logic sub-phase recorders (main-thread only) ---

    public static void recordLogicNetworkTime(long elapsedNs) {
        logicNetworkTimeNs += elapsedNs;
    }

    public static void recordLogicInputTime(long elapsedNs) {
        logicInputTimeNs += elapsedNs;
    }

    public static void recordLogicUIUpdateTime(long elapsedNs) {
        logicUIUpdateTimeNs += elapsedNs;
    }

    public static void recordLogicStatesUpdateTime(long elapsedNs) {
        logicStatesUpdateTimeNs += elapsedNs;
    }

    // --- WorldUpdate sub-phase recorders (main-thread only) ---

    public static void recordWorldUpdPreCellTime(long elapsedNs) {
        worldUpdPreCellTimeNs += elapsedNs;
    }

    public static void recordWorldUpdPostCollTime(long elapsedNs) {
        worldUpdPostCollTimeNs += elapsedNs;
    }

    // --- WorldSim sub-phase recorders (main-thread only) ---

    public static void recordWorldSimBulletTime(long elapsedNs) {
        worldSimBulletTimeNs += elapsedNs;
    }

    public static void recordWorldSimVehicleTime(long elapsedNs) {
        worldSimVehicleTimeNs += elapsedNs;
    }

    public static void recordWorldSimObjectTime(long elapsedNs) {
        worldSimObjectTimeNs += elapsedNs;
    }

    // --- CellUpdate sub-phase recorders (main-thread only) ---

    public static void recordCellUpdChunkMapTime(long elapsedNs) {
        cellUpdChunkMapTimeNs += elapsedNs;
    }

    public static void recordCellUpdProcessObjTime(long elapsedNs) {
        cellUpdProcessObjTimeNs += elapsedNs;
    }

    public static void recordCellUpdMiscTime(long elapsedNs) {
        cellUpdMiscTimeNs += elapsedNs;
    }

    // --- CollisionMgr sub-phase recorders (main-thread only) ---

    public static void recordCollPushableTime(long elapsedNs) {
        collPushableTimeNs += elapsedNs;
    }

    public static void recordCollContactSolveTime(long elapsedNs) {
        collContactSolveTimeNs += elapsedNs;
    }

    public static void recordCollPostUpdateTime(long elapsedNs) {
        collPostUpdateTimeNs += elapsedNs;
    }

    public static void recordCollSoundMgrTime(long elapsedNs) {
        collSoundMgrTimeNs += elapsedNs;
    }

    // --- Animation sub-phase recorders (called from parallel workers) ---

    public static void recordAnimAdvAnimatorTime(long elapsedNs) {
        animAdvAnimatorTimeNs.addAndGet(elapsedNs);
    }

    public static void recordAnimModelSlotUpdTime(long elapsedNs) {
        animModelSlotUpdTimeNs.addAndGet(elapsedNs);
    }

    public static void recordAnimLightInfoTime(long elapsedNs) {
        animLightInfoTimeNs.addAndGet(elapsedNs);
    }

    // --- NEW: CellRender detail recorders (main-thread only) ---

    public static void recordCellRenRecalcGridTime(long elapsedNs) {
        cellRenRecalcGridTimeNs += elapsedNs;
    }

    public static void recordCellRenFlattenFoliageTime(long elapsedNs) {
        cellRenFlattenFoliageTimeNs += elapsedNs;
    }

    public static void recordSpriteBatchStats(int batched, int legacy, int drawCalls) {
        spriteBatchCount += batched;
        spriteLegacyCount += legacy;
        spriteBatchDrawCalls += drawCalls;
    }

    public static void recordCellRenMinusFloorTime(long elapsedNs) {
        cellRenMinusFloorTimeNs += elapsedNs;
    }

    public static void recordCellRenDeferredCharsTime(long elapsedNs) {
        cellRenDeferredCharsTimeNs += elapsedNs;
    }

    public static void recordCellRenRenderCharsTime(long elapsedNs) {
        cellRenRenderCharsTimeNs += elapsedNs;
    }

    public static void recordCellRenFogTime(long elapsedNs) {
        cellRenFogTimeNs += elapsedNs;
    }

    public static void recordCellRenPuddlesTime(long elapsedNs) {
        cellRenPuddlesTimeNs += elapsedNs;
    }

    public static void recordCellRenWaterTime(long elapsedNs) {
        cellRenWaterTimeNs += elapsedNs;
    }

    public static void recordCellRenSnowTime(long elapsedNs) {
        cellRenSnowTimeNs += elapsedNs;
    }

    public static void recordCellRenBloodTime(long elapsedNs) {
        cellRenBloodTimeNs += elapsedNs;
    }

    public static void recordCellRenShadowsTime(long elapsedNs) {
        cellRenShadowsTimeNs += elapsedNs;
    }

    public static void recordCellRenMarkersTime(long elapsedNs) {
        cellRenMarkersTimeNs += elapsedNs;
    }

    // --- NEW: EndFrame detail recorders (main-thread only) ---

    public static void recordEndFrameMainTime(long elapsedNs) {
        endFrameMainTimeNs += elapsedNs;
    }

    public static void recordEndFrameTextTime(long elapsedNs) {
        endFrameTextTimeNs += elapsedNs;
    }

    public static void recordEndFrameUITime(long elapsedNs) {
        endFrameUITimeNs += elapsedNs;
    }

    // --- NEW: ProcessObj detail recorders (main-thread only) ---

    public static void recordCellUpdProcessItemsTime(long elapsedNs, int itemCount, int worldItemCount) {
        cellUpdProcessItemsTimeNs += elapsedNs;
        totalProcessItemsCount += itemCount;
        totalProcessWorldItemsCount += worldItemCount;
    }

    public static void recordCellUpdProcessIsoObjTime(long elapsedNs, int isoObjCount) {
        cellUpdProcessIsoObjTimeNs += elapsedNs;
        totalProcessIsoObjCount += isoObjCount;
    }

    public static void recordCellUpdProcessObjectsTime(long elapsedNs) {
        cellUpdProcessObjectsTimeNs += elapsedNs;
    }

    public static void recordCellUpdNetworkSendTime(long elapsedNs) {
        cellUpdNetworkSendTimeNs += elapsedNs;
    }

    // --- NEW: UpdateStuff detail recorders (main-thread only) ---

    public static void recordUpdStuffSoundTime(long elapsedNs) {
        updStuffSoundTimeNs += elapsedNs;
    }

    public static void recordUpdStuffFireTime(long elapsedNs) {
        updStuffFireTimeNs += elapsedNs;
    }

    public static void recordUpdStuffRainTime(long elapsedNs) {
        updStuffRainTimeNs += elapsedNs;
    }

    public static void recordUpdStuffVirtualZombTime(long elapsedNs) {
        updStuffVirtualZombTimeNs += elapsedNs;
    }

    public static void recordUpdStuffPathfindTime(long elapsedNs) {
        updStuffPathfindTimeNs += elapsedNs;
    }

    public static void recordUpdStuffPopTime(long elapsedNs) {
        updStuffPopTimeNs += elapsedNs;
    }

    public static void recordUpdStuffMiscTime(long elapsedNs) {
        updStuffMiscTimeNs += elapsedNs;
    }

    // --- NEW: WorldRender misc detail recorders (main-thread only) ---

    public static void recordWorldRenCursorTime(long elapsedNs) {
        worldRenCursorTimeNs += elapsedNs;
    }

    public static void recordWorldRenPolyMapTime(long elapsedNs) {
        worldRenPolyMapTimeNs += elapsedNs;
    }

    public static void recordWorldRenDebugTime(long elapsedNs) {
        worldRenDebugTimeNs += elapsedNs;
    }

    // --- NEW: Pre-cell detail recorders (main-thread only) ---

    public static void recordPreCellWeatherInitTime(long elapsedNs) {
        preCellWeatherInitTimeNs += elapsedNs;
    }

    public static void recordPreCellDeadBodyTime(long elapsedNs) {
        preCellDeadBodyTimeNs += elapsedNs;
    }

    public static void recordPreCellWorldItemTime(long elapsedNs) {
        preCellWorldItemTimeNs += elapsedNs;
    }

    // --- NEW: Other detail recorders (main-thread only) ---

    public static void recordRadioUpdateTime(long elapsedNs) {
        radioUpdateTimeNs += elapsedNs;
    }

    public static void recordModelMgrUpdateTime(long elapsedNs) {
        modelMgrUpdateTimeNs += elapsedNs;
    }

    // --- NEW: Logic detail recorders (main-thread only) ---

    public static void recordLogicSteamTime(long elapsedNs) {
        logicSteamTimeNs += elapsedNs;
    }

    public static void recordLogicSoundMgrTime(long elapsedNs) {
        logicSoundMgrTimeNs += elapsedNs;
    }

    // --- NEW: StartFrame overhead recorders (main-thread only) ---

    public static void recordStartFrameTime(long elapsedNs) {
        startFrameTimeNs += elapsedNs;
    }

    public static void recordStartFrameTextTime(long elapsedNs) {
        startFrameTextTimeNs += elapsedNs;
    }

    public static void recordStartFrameUITime(long elapsedNs) {
        startFrameUITimeNs += elapsedNs;
    }

    public static void recordUIRenderFrame() {
        uiRenderFrameCount++;
    }

    public static void recordUISkipFrame() {
        uiSkipFrameCount++;
    }

    public static void recordUIResizeTime(long elapsedNs) {
        uiResizeTimeNs += elapsedNs;
    }

    // --- NEW: CellRen remaining gap recorders (main-thread only) ---

    public static void recordCellRenLuaPostFloorTime(long elapsedNs) {
        cellRenLuaPostFloorTimeNs += elapsedNs;
    }

    public static void recordCellRenIsoMarkersTime(long elapsedNs) {
        cellRenIsoMarkersTimeNs += elapsedNs;
    }

    public static void recordCellRenImprovedFogTime(long elapsedNs) {
        cellRenImprovedFogTimeNs += elapsedNs;
    }

    public static void recordCellRenLoopOverheadTime(long elapsedNs) {
        cellRenLoopOverheadTimeNs += elapsedNs;
    }

    // --- NEW: Remaining gap recorders (main-thread only) ---

    public static void recordRenFramePostRenderTime(long elapsedNs) {
        renFramePostRenderTimeNs += elapsedNs;
    }

    public static void recordRenTextContentTime(long elapsedNs) {
        renTextContentTimeNs += elapsedNs;
    }

    public static void recordRenUIMiscTime(long elapsedNs) {
        renUIMiscTimeNs += elapsedNs;
    }

    public static void recordUpdInternalChunkMapTime(long elapsedNs) {
        updInternalChunkMapTimeNs += elapsedNs;
    }

    public static void recordUpdInternalPostTickTime(long elapsedNs) {
        updInternalPostTickTimeNs += elapsedNs;
    }

    public static void recordWorldUpdMiscTime(long elapsedNs) {
        worldUpdMiscTimeNs += elapsedNs;
    }

    public static void recordOnRenderTickTime(long elapsedNs) {
        onRenderTickTimeNs += elapsedNs;
    }

    public static void recordLogicPreStatesTime(long elapsedNs) {
        logicPreStatesTimeNs += elapsedNs;
    }

    public static void recordLogicPostStatesTime(long elapsedNs) {
        logicPostStatesTimeNs += elapsedNs;
    }

    public static void recordWorldUpdVehicleTime(long elapsedNs) {
        worldUpdVehicleTimeNs += elapsedNs;
    }

    public static void recordUpdStuffPreTimedTime(long elapsedNs) {
        updStuffPreTimedTimeNs += elapsedNs;
    }

    public static void recordUpdStuffMetaTime(long elapsedNs) {
        updStuffMetaTimeNs += elapsedNs;
    }

    public static void recordUpdStuffMapCollTime(long elapsedNs) {
        updStuffMapCollTimeNs += elapsedNs;
    }

    public static void recordRenUIPreUIMgrTime(long elapsedNs) {
        renUIPreUIMgrTimeNs += elapsedNs;
    }

    public static void recordCellRenPostTilesTime(long elapsedNs) {
        cellRenPostTilesTimeNs += elapsedNs;
    }

    public static void recordCellRenSetupTime(long elapsedNs) {
        cellRenSetupTimeNs += elapsedNs;
    }

    /** Record batch count from a single ringBuffer.render() call (render-thread only). */
    public static void recordBatchCount(int count) {
        batchCount += count;
        batchCallCount++;
    }

    /** Add to the zombie count for this frame (accumulated across all simulation buckets). */
    public static void setZombieCount(int count) {
        zombieCount += count;
    }

    /**
     * Call once per frame from the main thread (in MovingObjectUpdateScheduler.update()).
     * Handles periodic logging.
     */
    public static void endFrame() {
        if (!Core.bDebug) return;
        frameCount++;
        long now = System.nanoTime();
        if (now - lastLogTime >= LOG_INTERVAL_NS) {
            if (frameCount > 0) {
                long sepNs = separateTimeNs.get();
                long updNs = updateTimeNs.get();
                long postNs = postupdateTimeNs.get();
                long drnNs = drainTimeNs.get();
                long renNs = renderTimeNs.get();
                int rendered = renderedZombieCount.get();
                int culled = sceneCulledZombieCount.get();
                float avgSeparateMs = (sepNs / 1_000_000.0f) / frameCount;
                float avgUpdateMs = (updNs / 1_000_000.0f) / frameCount;
                float avgPostupdateMs = (postNs / 1_000_000.0f) / frameCount;
                float avgDrainMs = (drnNs / 1_000_000.0f) / frameCount;
                float avgRenderMs = (renNs / 1_000_000.0f) / frameCount;
                float totalAvgMs = avgSeparateMs + avgUpdateMs + avgPostupdateMs;
                int sepCalls = separateCallCount.get();
                int sepSkips = separateSkipCount.get();
                int sepThrottles = separateThrottleCount.get();
                int avgSepCalls = sepCalls / frameCount;
                int avgSepSkips = sepSkips / frameCount;
                int avgSepThrottles = sepThrottles / frameCount;
                int visSkips = visibilitySkipCount.get();
                int visThrottles = visibilityThrottleCount.get();
                int animThrottles = animThrottleCount.get();
                int defQueued = deferredEventCount.get();
                int defDrained = drainedEventCount.get();
                int avgDefPerFrame = defDrained / frameCount;

                int avgAnimThrottles = animThrottles / frameCount;
                int aiThrottles = aiThrottleCount.get();
                int avgAiThrottles = aiThrottles / frameCount;
                int lightUpd = lightUpdateCount.get();
                int lightThr = lightThrottleCount.get();
                float avgBoneMs = (bonePrecomputeTimeNs.get() / 1_000_000.0f) / frameCount;
                int avgBoneSkips = boneSkipCount.get() / frameCount;
                float avgPostCollMs = (postCollisionTimeNs.get() / 1_000_000.0f) / frameCount;
                float avgPostAnimMs = (postAnimTimeNs.get() / 1_000_000.0f) / frameCount;
                float avgPostLuaMs = (postLuaDrainTimeNs.get() / 1_000_000.0f) / frameCount;
                int avgPostAnimCnt = postAnimCountTotal.get() / frameCount;

                // Broad frame phases
                float avgFrameMs = (frameStepTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicMs = (logicTimeNs / 1_000_000.0f) / frameCount;
                float avgWorldUpdMs = (worldUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgCellUpdMs = (cellUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgUpdStuffMs = (updateStuffTimeNs / 1_000_000.0f) / frameCount;
                float avgLuaTickMs = (luaOnTickTimeNs / 1_000_000.0f) / frameCount;
                float avgStatesRenMs = (statesRenderTimeNs / 1_000_000.0f) / frameCount;
                float avgCellRenMs = (cellRenderTimeNs / 1_000_000.0f) / frameCount;
                float avgLightMs = (lightingTimeNs / 1_000_000.0f) / frameCount;
                float avgFrameReadyMs = (doFrameReadyTimeNs / 1_000_000.0f) / frameCount;
                float avgWorldSimMs = (worldSimTimeNs / 1_000_000.0f) / frameCount;
                float avgCollMgrMs = (collisionMgrTimeNs / 1_000_000.0f) / frameCount;
                float avgSceneCullMs = (sceneCullTimeNs / 1_000_000.0f) / frameCount;

                int avgZombieCount = zombieCount / frameCount;

                // Compute all remaining averages
                float avgAnimAdvMs = (animAdvAnimatorTimeNs.get() / 1_000_000.0f) / frameCount;
                float avgAnimSlotMs = (animModelSlotUpdTimeNs.get() / 1_000_000.0f) / frameCount;
                float avgAnimLightMs = (animLightInfoTimeNs.get() / 1_000_000.0f) / frameCount;
                float avgRenFrameMs = (renderFrameTimeNs / 1_000_000.0f) / frameCount;
                float avgRenFrameTextMs = (renderFrameTextTimeNs / 1_000_000.0f) / frameCount;
                float avgRenFrameUIMs = (renderFrameUITimeNs / 1_000_000.0f) / frameCount;
                float avgModelOutMs = (modelOutlinesTimeNs / 1_000_000.0f) / frameCount;
                float avgWorldRenMs = (isoWorldRenderTimeNs / 1_000_000.0f) / frameCount;
                float avgEndFrameMs = (coreEndFrameTimeNs / 1_000_000.0f) / frameCount;
                float avgOffscreenMs = (offscreenBufferTimeNs / 1_000_000.0f) / frameCount;
                float avgLightJNIMs = (lightingJNITimeNs / 1_000_000.0f) / frameCount;
                float avgObjPickerMs = (objectPickerTimeNs / 1_000_000.0f) / frameCount;
                float avgUIMgrMs = (uiManagerRenderTimeNs / 1_000_000.0f) / frameCount;
                float avgWorldMiscMs = (worldRenderMiscTimeNs / 1_000_000.0f) / frameCount;
                float avgPreCellMs = (renderPreCellTimeNs / 1_000_000.0f) / frameCount;
                float avgOnRenTickMs = (onRenderTickTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicNetMs = (logicNetworkTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicInMs = (logicInputTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicUIMs = (logicUIUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicStMs = (logicStatesUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicSteamMs = (logicSteamTimeNs / 1_000_000.0f) / frameCount;
                float avgLogicSndMgrMs = (logicSoundMgrTimeNs / 1_000_000.0f) / frameCount;
                float avgUIChunkMs = (updInternalChunkMapTimeNs / 1_000_000.0f) / frameCount;
                float avgUIPostMs = (updInternalPostTickTimeNs / 1_000_000.0f) / frameCount;
                float avgRadioMs = (radioUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgModelMgrMs = (modelMgrUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgPreStMs = (logicPreStatesTimeNs / 1_000_000.0f) / frameCount;
                float avgPostStMs = (logicPostStatesTimeNs / 1_000_000.0f) / frameCount;
                float avgPreCellMiscMs = (worldUpdPreCellTimeNs / 1_000_000.0f) / frameCount;
                float avgPostCollMiscMs = (worldUpdPostCollTimeNs / 1_000_000.0f) / frameCount;
                float avgWUMiscMs = (worldUpdMiscTimeNs / 1_000_000.0f) / frameCount;
                float avgColPostMs2 = (collPostUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgColSndMs2 = (collSoundMgrTimeNs / 1_000_000.0f) / frameCount;
                float avgVehicleMs = (worldUpdVehicleTimeNs / 1_000_000.0f) / frameCount;
                float avgWsBulletMs = (worldSimBulletTimeNs / 1_000_000.0f) / frameCount;
                float avgWsVehicleMs = (worldSimVehicleTimeNs / 1_000_000.0f) / frameCount;
                float avgWsObjectMs = (worldSimObjectTimeNs / 1_000_000.0f) / frameCount;
                float avgCuChunkMs = (cellUpdChunkMapTimeNs / 1_000_000.0f) / frameCount;
                float avgCuProcMs = (cellUpdProcessObjTimeNs / 1_000_000.0f) / frameCount;
                float avgCuMiscMs = (cellUpdMiscTimeNs / 1_000_000.0f) / frameCount;
                float avgColPushMs = (collPushableTimeNs / 1_000_000.0f) / frameCount;
                float avgColSolveMs = (collContactSolveTimeNs / 1_000_000.0f) / frameCount;
                float avgColPostMs = (collPostUpdateTimeNs / 1_000_000.0f) / frameCount;
                float avgColSndMs = (collSoundMgrTimeNs / 1_000_000.0f) / frameCount;
                int avgBatches = batchCallCount > 0 ? batchCount / batchCallCount : 0;
                int avgBatchesPerFrame = batchCount / frameCount;
                float avgSFMs = (startFrameTimeNs / 1_000_000.0f) / frameCount;
                float avgSFTMs = (startFrameTextTimeNs / 1_000_000.0f) / frameCount;
                float avgSFUIMs = (startFrameUITimeNs / 1_000_000.0f) / frameCount;
                float avgUIResMs = (uiResizeTimeNs / 1_000_000.0f) / frameCount;
                float avgRFPostMs = (renFramePostRenderTimeNs / 1_000_000.0f) / frameCount;
                float avgRTCntMs = (renTextContentTimeNs / 1_000_000.0f) / frameCount;
                float avgRUMiscMs = (renUIMiscTimeNs / 1_000_000.0f) / frameCount;
                float avgPreUIMgrMs = (renUIPreUIMgrTimeNs / 1_000_000.0f) / frameCount;
                float avgEFMainMs = (endFrameMainTimeNs / 1_000_000.0f) / frameCount;
                float avgEFTextMs = (endFrameTextTimeNs / 1_000_000.0f) / frameCount;
                float avgEFUIMs = (endFrameUITimeNs / 1_000_000.0f) / frameCount;
                float avgCRGridMs = (cellRenGridStacksTimeNs / 1_000_000.0f) / frameCount;
                float avgCRCutawayMs = (cellRenCutawayTimeNs / 1_000_000.0f) / frameCount;
                float avgCRFloorMs = (cellRenFloorTimeNs / 1_000_000.0f) / frameCount;
                float avgCRFloorShadMs = (cellRenFloorShadingTimeNs / 1_000_000.0f) / frameCount;
                float avgCRBldgShadMs = (cellRenBuildingShadowTimeNs / 1_000_000.0f) / frameCount;
                float avgCREnvMs = (cellRenEnvironmentTimeNs / 1_000_000.0f) / frameCount;
                float avgCRVegMs = (cellRenVegCorpsesTimeNs / 1_000_000.0f) / frameCount;
                float avgCRCharMs = (cellRenCharactersTimeNs / 1_000_000.0f) / frameCount;
                float avgCRLastMs = (cellRenRenderLastTimeNs / 1_000_000.0f) / frameCount;
                float avgCRLuaPFMs = (cellRenLuaPostFloorTimeNs / 1_000_000.0f) / frameCount;
                float avgCRIsoMrkMs = (cellRenIsoMarkersTimeNs / 1_000_000.0f) / frameCount;
                float avgCRFogOHMs = (cellRenImprovedFogTimeNs / 1_000_000.0f) / frameCount;
                float avgCRPostTMs = (cellRenPostTilesTimeNs / 1_000_000.0f) / frameCount;
                float avgCRSetupMs = (cellRenSetupTimeNs / 1_000_000.0f) / frameCount;
                float avgRecalcMs = (cellRenRecalcGridTimeNs / 1_000_000.0f) / frameCount;
                float avgFoliageMs = (cellRenFlattenFoliageTimeNs / 1_000_000.0f) / frameCount;
                float avgCutawayMs = avgCRCutawayMs;
                float avgMFMs = (cellRenMinusFloorTimeNs / 1_000_000.0f) / frameCount;
                float avgDCMs = (cellRenDeferredCharsTimeNs / 1_000_000.0f) / frameCount;
                float avgRCMs = (cellRenRenderCharsTimeNs / 1_000_000.0f) / frameCount;
                float avgFogMs = (cellRenFogTimeNs / 1_000_000.0f) / frameCount;
                float avgPuddMs = (cellRenPuddlesTimeNs / 1_000_000.0f) / frameCount;
                float avgWatMs = (cellRenWaterTimeNs / 1_000_000.0f) / frameCount;
                float avgSnowMs = (cellRenSnowTimeNs / 1_000_000.0f) / frameCount;
                float avgBldMs = (cellRenBloodTimeNs / 1_000_000.0f) / frameCount;
                float avgShadMs = (cellRenShadowsTimeNs / 1_000_000.0f) / frameCount;
                float avgMrkMs = (cellRenMarkersTimeNs / 1_000_000.0f) / frameCount;
                float avgLoopOHMs = (cellRenLoopOverheadTimeNs / 1_000_000.0f) / frameCount;
                float avgProcItemsMs = (cellUpdProcessItemsTimeNs / 1_000_000.0f) / frameCount;
                float avgProcIsoMs = (cellUpdProcessIsoObjTimeNs / 1_000_000.0f) / frameCount;
                float avgProcObjMs = (cellUpdProcessObjectsTimeNs / 1_000_000.0f) / frameCount;
                float avgNetSendMs = (cellUpdNetworkSendTimeNs / 1_000_000.0f) / frameCount;
                float avgUSndMs = (updStuffSoundTimeNs / 1_000_000.0f) / frameCount;
                float avgUFireMs = (updStuffFireTimeNs / 1_000_000.0f) / frameCount;
                float avgURainMs = (updStuffRainTimeNs / 1_000_000.0f) / frameCount;
                float avgUVZMs = (updStuffVirtualZombTimeNs / 1_000_000.0f) / frameCount;
                float avgUPFMs = (updStuffPathfindTimeNs / 1_000_000.0f) / frameCount;
                float avgUPopMs = (updStuffPopTimeNs / 1_000_000.0f) / frameCount;
                float avgUMiscMs = (updStuffMiscTimeNs / 1_000_000.0f) / frameCount;
                float avgUPreMs = (updStuffPreTimedTimeNs / 1_000_000.0f) / frameCount;
                float avgUMetaMs = (updStuffMetaTimeNs / 1_000_000.0f) / frameCount;
                float avgUMapCMs = (updStuffMapCollTimeNs / 1_000_000.0f) / frameCount;
                float avgWInitMs = (preCellWeatherInitTimeNs / 1_000_000.0f) / frameCount;
                float avgDBMs = (preCellDeadBodyTimeNs / 1_000_000.0f) / frameCount;
                float avgWIMs = (preCellWorldItemTimeNs / 1_000_000.0f) / frameCount;
                float avgCursMs = (worldRenCursorTimeNs / 1_000_000.0f) / frameCount;
                float avgPolyMs = (worldRenPolyMapTimeNs / 1_000_000.0f) / frameCount;
                float avgDbgMs = (worldRenDebugTimeNs / 1_000_000.0f) / frameCount;

                // === Zombie summary (compact, one line) ===
                DebugLog.log(String.format(
                    "[ZombPerf:Zombies] %d frames, %d zombies/f | update=%.1f, separate=%.1f, postupdate=%.1f [coll=%.1f, anim=%.1f(%d), luaDrain=%.2f] | render=%.1f, rendered=%d, culled=%d | sep: %d skip/%d thr | bones=%.1f skip=%d | aiThr=%d",
                    frameCount, avgZombieCount, avgUpdateMs, avgSeparateMs, avgPostupdateMs,
                    avgPostCollMs, avgPostAnimMs, avgPostAnimCnt, avgPostLuaMs,
                    avgRenderMs, rendered, culled,
                    avgSepSkips, avgSepThrottles,
                    avgBoneMs, avgBoneSkips,
                    avgAiThrottles
                ));

                // === Sprite batch summary ===
                int avgBatched = (int)(spriteBatchCount / frameCount);
                int avgLegacy = (int)(spriteLegacyCount / frameCount);
                int avgDrawCalls = (int)(spriteBatchDrawCalls / frameCount);
                if (avgBatched > 0 || avgLegacy > 0) {
                    DebugLog.log(String.format(
                        "[ZombPerf:SpriteBatch] batched=%d/f, legacy=%d/f, drawCalls=%d/f",
                        avgBatched, avgLegacy, avgDrawCalls
                    ));
                }

                // === Item summary (compact, one line) ===
                int avgItemCount = (int)(totalProcessItemsCount / frameCount);
                int avgWorldItemCount = (int)(totalProcessWorldItemsCount / frameCount);
                int avgIsoObjCount = (int)(totalProcessIsoObjCount / frameCount);
                DebugLog.log(String.format(
                    "[ZombPerf:Items] %d frames | items=%d/f (%.2fms), worldItems=%d/f, isoObj=%d/f (%.2fms)",
                    frameCount, avgItemCount, avgProcItemsMs, avgWorldItemCount, avgIsoObjCount, avgProcIsoMs
                ));

                // === Frame tree ===
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("[ZombPerf] frameStep = %.1fms (%d frames, %d batches/f)", avgFrameMs, frameCount, avgBatchesPerFrame));

                // -- logic --
                t(sb, 1, "logic", avgLogicMs);
                t(sb, 2, "network", avgLogicNetMs);
                t(sb, 2, "input", avgLogicInMs);
                  t(sb, 3, "steam", avgLogicSteamMs);
                t(sb, 2, "uiUpdate", avgLogicUIMs);
                  t(sb, 3, "soundMgr", avgLogicSndMgrMs);
                t(sb, 2, "preStates", avgPreStMs);
                t(sb, 2, "statesUpdate", avgLogicStMs);
                  t(sb, 3, "chunkMap", avgUIChunkMs);
                  t(sb, 3, "worldUpd", avgWorldUpdMs);
                    t(sb, 4, "vehicle", avgVehicleMs);
                    t(sb, 4, "worldSim", avgWorldSimMs);
                      t(sb, 5, "bullet", avgWsBulletMs);
                      t(sb, 5, "vehicles", avgWsVehicleMs);
                      t(sb, 5, "objects", avgWsObjectMs);
                    t(sb, 4, "preCellMisc", avgPreCellMiscMs);
                    t(sb, 4, "cellUpd", avgCellUpdMs);
                      t(sb, 5, "chunkMap", avgCuChunkMs);
                      t(sb, 5, "processObj", avgCuProcMs);
                        t(sb, 6, "items", avgProcItemsMs);
                        t(sb, 6, "isoObj", avgProcIsoMs);
                        t(sb, 6, "objects", avgProcObjMs);
                        t(sb, 6, "netSend", avgNetSendMs);
                      t(sb, 5, "misc", avgCuMiscMs);
                    t(sb, 4, "wuMisc", avgWUMiscMs);
                    t(sb, 4, "collMgr", avgCollMgrMs);
                      t(sb, 5, "pushables", avgColPushMs);
                      t(sb, 5, "contactSolve", avgColSolveMs);
                      t(sb, 5, "postUpdate", avgColPostMs);
                      t(sb, 5, "soundMgrs", avgColSndMs);
                    t(sb, 4, "postupdate", avgColPostMs2);
                      t(sb, 5, "collision", avgPostCollMs);
                      t(sb, 5, "animation", avgPostAnimMs);
                        t(sb, 6, "advAnimator", avgAnimAdvMs);
                        t(sb, 6, "modelSlotUpd", avgAnimSlotMs);
                        t(sb, 6, "lightInfo", avgAnimLightMs);
                      t(sb, 5, "luaDrain", avgPostLuaMs);
                    t(sb, 4, "soundMgrs", avgColSndMs2);
                    t(sb, 4, "postCollMisc", avgPostCollMiscMs);
                  t(sb, 3, "radio", avgRadioMs);
                  t(sb, 3, "updStuff", avgUpdStuffMs);
                    t(sb, 4, "preTimed", avgUPreMs);
                    t(sb, 4, "sound", avgUSndMs);
                    t(sb, 4, "fire", avgUFireMs);
                    t(sb, 4, "rain", avgURainMs);
                    t(sb, 4, "meta", avgUMetaMs);
                    t(sb, 4, "virtualZomb", avgUVZMs);
                    t(sb, 4, "mapColl", avgUMapCMs);
                    t(sb, 4, "pathfind", avgUPFMs);
                    t(sb, 4, "pop", avgUPopMs);
                    t(sb, 4, "misc", avgUMiscMs);
                  t(sb, 3, "luaTick", avgLuaTickMs);
                  t(sb, 3, "modelMgr", avgModelMgrMs);
                  t(sb, 3, "postTick", avgUIPostMs);
                t(sb, 2, "postStates", avgPostStMs);

                // -- statesRender --
                t(sb, 1, "statesRender", avgStatesRenMs);
                t(sb, 2, "lightJNI", avgLightJNIMs);
                t(sb, 2, "objPicker", avgObjPickerMs);
                t(sb, 2, "startFrame", avgSFMs);
                t(sb, 2, "renderFrame", avgRenFrameMs);
                  t(sb, 3, "outlines", avgModelOutMs);
                  t(sb, 3, "worldRen", avgWorldRenMs);
                    t(sb, 4, "sceneCull", avgSceneCullMs);
                    t(sb, 4, "bones", avgBoneMs);
                    t(sb, 4, "preCellAtlas", avgPreCellMs);
                      t(sb, 5, "weatherInit", avgWInitMs);
                      t(sb, 5, "deadBody", avgDBMs);
                      t(sb, 5, "worldItem", avgWIMs);
                    t(sb, 4, "cellRen", avgCellRenMs);
                      t(sb, 5, "setup", avgCRSetupMs);
                      t(sb, 5, "gridStacks", avgCRGridMs);
                        t(sb, 6, "recalc", avgRecalcMs);
                        t(sb, 6, "foliage", avgFoliageMs);
                        t(sb, 6, "cutaway", avgCutawayMs);
                      t(sb, 5, "floor", avgCRFloorMs);
                      t(sb, 5, "floorShading", avgCRFloorShadMs);
                      t(sb, 5, "bldgShadow", avgCRBldgShadMs);
                      t(sb, 5, "env", avgCREnvMs);
                        t(sb, 6, "puddles", avgPuddMs);
                        t(sb, 6, "water", avgWatMs);
                        t(sb, 6, "snow", avgSnowMs);
                        t(sb, 6, "blood", avgBldMs);
                        t(sb, 6, "shadows", avgShadMs);
                        t(sb, 6, "markers", avgMrkMs);
                      t(sb, 5, "vegCorpses", avgCRVegMs);
                      t(sb, 5, "characters", avgCRCharMs);
                        t(sb, 6, "minusFloor", avgMFMs);
                        t(sb, 6, "deferred", avgDCMs);
                        t(sb, 6, "renderChars", avgRCMs);
                        t(sb, 6, "fog", avgFogMs);
                        t(sb, 6, "loopOH", avgLoopOHMs);
                      t(sb, 5, "luaPostFloor", avgCRLuaPFMs);
                      t(sb, 5, "isoMarkers", avgCRIsoMrkMs);
                      t(sb, 5, "renderLast", avgCRLastMs);
                      t(sb, 5, "postTiles", avgCRPostTMs);
                      t(sb, 5, "fog", avgCRFogOHMs);
                    t(sb, 4, "worldMisc", avgWorldMiscMs);
                      t(sb, 5, "cursor", avgCursMs);
                      t(sb, 5, "polyMap", avgPolyMs);
                      t(sb, 5, "debug", avgDbgMs);
                  t(sb, 3, "postRen", avgRFPostMs);
                  t(sb, 3, "endFrame", avgEFMainMs);
                t(sb, 2, "offscreen", avgOffscreenMs);
                t(sb, 2, "startFrameText", avgSFTMs);
                t(sb, 2, "renText", avgRenFrameTextMs);
                  t(sb, 3, "content", avgRTCntMs);
                  t(sb, 3, "endFText", avgEFTextMs);
                t(sb, 2, "uiResize", avgUIResMs);
                t(sb, 2, "startFrameUI", avgSFUIMs);
                t(sb, 2, "renUI", avgRenFrameUIMs);
                  t(sb, 3, "preUIMgr", avgPreUIMgrMs);
                  t(sb, 3, "uiMgr", avgUIMgrMs);
                  t(sb, 3, "uiMisc", avgRUMiscMs);
                  t(sb, 3, "endFUI", avgEFUIMs);
                sb.append(String.format("\n[ZombPerf]     uiFrames: %d render / %d skip", uiRenderFrameCount, uiSkipFrameCount));

                // -- post-render --
                t(sb, 1, "onRenderTick", avgOnRenTickMs);
                t(sb, 1, "lighting", avgLightMs);
                t(sb, 1, "frameReady", avgFrameReadyMs);

                DebugLog.log(sb.toString());
            }

            // Reset all accumulators
            zombieCount = 0;
            separateTimeNs.set(0);
            updateTimeNs.set(0);
            postupdateTimeNs.set(0);
            drainTimeNs.set(0);
            separateCallCount.set(0);
            separateSkipCount.set(0);
            separateThrottleCount.set(0);
            visibilitySkipCount.set(0);
            visibilityThrottleCount.set(0);
            animThrottleCount.set(0);
            aiThrottleCount.set(0);
            deferredEventCount.set(0);
            drainedEventCount.set(0);
            renderTimeNs.set(0);
            sceneCulledZombieCount.set(0);
            lightUpdateCount.set(0);
            lightThrottleCount.set(0);
            bonePrecomputeTimeNs.set(0);
            boneSkipCount.set(0);
            postCollisionTimeNs.set(0);
            postAnimTimeNs.set(0);
            postLuaDrainTimeNs.set(0);
            postAnimCountTotal.set(0);

            // Reset UI frame counters
            uiRenderFrameCount = 0;
            uiSkipFrameCount = 0;

            // Reset batch count accumulators
            batchCount = 0;
            batchCallCount = 0;

            // Reset broad frame phase accumulators
            frameStepTimeNs = 0;
            logicTimeNs = 0;
            worldUpdateTimeNs = 0;
            cellUpdateTimeNs = 0;
            updateStuffTimeNs = 0;
            luaOnTickTimeNs = 0;
            statesRenderTimeNs = 0;
            cellRenderTimeNs = 0;
            lightingTimeNs = 0;
            doFrameReadyTimeNs = 0;
            worldSimTimeNs = 0;
            collisionMgrTimeNs = 0;
            sceneCullTimeNs = 0;

            // Reset CellRender sub-phase accumulators
            cellRenGridStacksTimeNs = 0;
            cellRenCutawayTimeNs = 0;
            cellRenFloorTimeNs = 0;
            cellRenFloorShadingTimeNs = 0;
            cellRenBuildingShadowTimeNs = 0;
            cellRenEnvironmentTimeNs = 0;
            cellRenVegCorpsesTimeNs = 0;
            cellRenCharactersTimeNs = 0;
            cellRenRenderLastTimeNs = 0;

            // Reset render sub-phase accumulators
            renderFrameTimeNs = 0;
            renderFrameTextTimeNs = 0;
            renderFrameUITimeNs = 0;
            modelOutlinesTimeNs = 0;
            isoWorldRenderTimeNs = 0;
            coreEndFrameTimeNs = 0;
            offscreenBufferTimeNs = 0;
            lightingJNITimeNs = 0;
            objectPickerTimeNs = 0;
            uiManagerRenderTimeNs = 0;
            worldRenderMiscTimeNs = 0;
            renderPreCellTimeNs = 0;

            // Reset Logic sub-phase accumulators
            logicNetworkTimeNs = 0;
            logicInputTimeNs = 0;
            logicUIUpdateTimeNs = 0;
            logicStatesUpdateTimeNs = 0;

            // Reset WorldUpdate sub-phase accumulators
            worldUpdPreCellTimeNs = 0;
            worldUpdPostCollTimeNs = 0;

            // Reset WorldSim sub-phase accumulators
            worldSimBulletTimeNs = 0;
            worldSimVehicleTimeNs = 0;
            worldSimObjectTimeNs = 0;

            // Reset CellUpdate sub-phase accumulators
            cellUpdChunkMapTimeNs = 0;
            cellUpdProcessObjTimeNs = 0;
            cellUpdMiscTimeNs = 0;

            // Reset CollisionMgr sub-phase accumulators
            collPushableTimeNs = 0;
            collContactSolveTimeNs = 0;
            collPostUpdateTimeNs = 0;
            collSoundMgrTimeNs = 0;

            // Reset animation sub-phase accumulators
            animAdvAnimatorTimeNs.set(0);
            animModelSlotUpdTimeNs.set(0);
            animLightInfoTimeNs.set(0);

            // Reset sprite batcher stats
            spriteBatchCount = 0;
            spriteLegacyCount = 0;
            spriteBatchDrawCalls = 0;

            // Reset NEW detail accumulators
            cellRenRecalcGridTimeNs = 0;
            cellRenFlattenFoliageTimeNs = 0;
            cellRenMinusFloorTimeNs = 0;
            cellRenDeferredCharsTimeNs = 0;
            cellRenRenderCharsTimeNs = 0;
            cellRenFogTimeNs = 0;
            cellRenPuddlesTimeNs = 0;
            cellRenWaterTimeNs = 0;
            cellRenSnowTimeNs = 0;
            cellRenBloodTimeNs = 0;
            cellRenShadowsTimeNs = 0;
            cellRenMarkersTimeNs = 0;
            endFrameMainTimeNs = 0;
            endFrameTextTimeNs = 0;
            endFrameUITimeNs = 0;
            cellUpdProcessItemsTimeNs = 0;
            cellUpdProcessIsoObjTimeNs = 0;
            cellUpdProcessObjectsTimeNs = 0;
            cellUpdNetworkSendTimeNs = 0;
            totalProcessItemsCount = 0;
            totalProcessWorldItemsCount = 0;
            totalProcessIsoObjCount = 0;
            updStuffSoundTimeNs = 0;
            updStuffFireTimeNs = 0;
            updStuffRainTimeNs = 0;
            updStuffVirtualZombTimeNs = 0;
            updStuffPathfindTimeNs = 0;
            updStuffPopTimeNs = 0;
            updStuffMiscTimeNs = 0;
            worldRenCursorTimeNs = 0;
            worldRenPolyMapTimeNs = 0;
            worldRenDebugTimeNs = 0;
            preCellWeatherInitTimeNs = 0;
            preCellDeadBodyTimeNs = 0;
            preCellWorldItemTimeNs = 0;
            radioUpdateTimeNs = 0;
            modelMgrUpdateTimeNs = 0;
            logicSteamTimeNs = 0;
            logicSoundMgrTimeNs = 0;
            startFrameTimeNs = 0;
            startFrameTextTimeNs = 0;
            startFrameUITimeNs = 0;
            uiResizeTimeNs = 0;
            cellRenLuaPostFloorTimeNs = 0;
            cellRenIsoMarkersTimeNs = 0;
            cellRenImprovedFogTimeNs = 0;
            cellRenLoopOverheadTimeNs = 0;
            renFramePostRenderTimeNs = 0;
            renTextContentTimeNs = 0;
            renUIMiscTimeNs = 0;
            updInternalChunkMapTimeNs = 0;
            updInternalPostTickTimeNs = 0;
            worldUpdMiscTimeNs = 0;
            onRenderTickTimeNs = 0;
            logicPreStatesTimeNs = 0;
            logicPostStatesTimeNs = 0;
            worldUpdVehicleTimeNs = 0;
            updStuffPreTimedTimeNs = 0;
            updStuffMetaTimeNs = 0;
            updStuffMapCollTimeNs = 0;
            renUIPreUIMgrTimeNs = 0;
            cellRenPostTilesTimeNs = 0;
            cellRenSetupTimeNs = 0;

            frameCount = 0;
            lastLogTime = now;
        }
    }

    /** Append a tree line to the log buffer. Lines < 0.05ms are hidden. */
    private static void t(StringBuilder sb, int depth, String name, float ms) {
        if (ms < 0.05f) return;
        sb.append("\n[ZombPerf]   ");
        for (int i = 0; i < depth; i++) sb.append("  ");
        if (depth >= 2) sb.append("|- ");
        sb.append(name).append(" = ").append(String.format("%.1f", ms));
    }
}

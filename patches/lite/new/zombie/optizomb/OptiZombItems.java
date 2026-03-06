package zombie.optizomb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import zombie.debug.DebugLog;
import zombie.inventory.InventoryItem;

/**
 * OptiZomb Item Container optimization (Opt 28).
 *
 * Sub-optimizations:
 *   28d: Type index - HashMap on ItemContainer for O(1) type lookups
 *   28e: FindAndReturn - HashSet skip set for O(1) exclusion checks
 *   28g: needsProcessing() - lazy ProcessItems filtering at chunk load
 */
public final class OptiZombItems {

    // Diagnostics
    private static long lastReportTime = 0;
    private static int typeIndexHits = 0;
    private static int typeIndexFallbacks = 0;
    private static int findAndReturnSkipSets = 0;
    private static int processItemsFiltered = 0;
    private static int processItemsTotal = 0;

    // --- Type Index (28d) ---

    public static void typeIndexAdd(HashMap<String, ArrayList<InventoryItem>> index, InventoryItem item) {
        if (item == null) return;
        String t = item.getType();
        String ft = item.getFullType();
        if (t != null) {
            index.computeIfAbsent(t, k -> new ArrayList<>()).add(item);
        }
        if (ft != null && !ft.equals(t)) {
            index.computeIfAbsent(ft, k -> new ArrayList<>()).add(item);
        }
    }

    public static void typeIndexRemove(HashMap<String, ArrayList<InventoryItem>> index, InventoryItem item) {
        if (item == null) return;
        String t = item.getType();
        String ft = item.getFullType();
        if (t != null) {
            ArrayList<InventoryItem> list = index.get(t);
            if (list != null) {
                list.remove(item);
                if (list.isEmpty()) index.remove(t);
            }
        }
        if (ft != null && !ft.equals(t)) {
            ArrayList<InventoryItem> list = index.get(ft);
            if (list != null) {
                list.remove(item);
                if (list.isEmpty()) index.remove(ft);
            }
        }
    }

    public static void typeIndexClear(HashMap<String, ArrayList<InventoryItem>> index) {
        index.clear();
    }

    public static void typeIndexRebuild(HashMap<String, ArrayList<InventoryItem>> index, ArrayList<InventoryItem> items) {
        index.clear();
        for (int i = 0; i < items.size(); i++) {
            typeIndexAdd(index, items.get(i));
        }
    }

    /**
     * Get all items matching a type key (short type or fullType).
     * Returns null for "/" delimited multi-type queries (caller falls back to linear scan).
     */
    public static ArrayList<InventoryItem> typeIndexGet(HashMap<String, ArrayList<InventoryItem>> index, String typeKey) {
        if (typeKey == null || typeKey.indexOf('/') >= 0) return null;
        ArrayList<InventoryItem> result = index.get(typeKey);
        if (result != null) {
            typeIndexHits++;
        } else {
            typeIndexFallbacks++;
        }
        return result;
    }

    // --- FindAndReturn skip set (28e) ---

    public static InventoryItem findAndReturnWithSkipSet(
            ArrayList<InventoryItem> items, String type, ArrayList<InventoryItem> skipList,
            java.util.function.BiPredicate<String, InventoryItem> compareType) {
        findAndReturnSkipSets++;
        HashSet<InventoryItem> skipSet = new HashSet<>(skipList);
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            if (item.getType() != null && compareType.test(type, item) && !skipSet.contains(item)) {
                return item;
            }
        }
        return null;
    }

    // --- ProcessItems filtering (28g) ---

    public static void recordProcessItemsFiltered(int filtered, int total) {
        processItemsFiltered += filtered;
        processItemsTotal += total;
    }

    // --- Diagnostics ---

    public static void reportIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 5000) return;
        lastReportTime = now;

        if (!OptiZombConfig.ITEMS) return;
        if (typeIndexHits == 0 && typeIndexFallbacks == 0 && processItemsTotal == 0 && findAndReturnSkipSets == 0) return;

        DebugLog.General.println("[OptiZomb] ITEMS (5.0s): typeIndex=" + typeIndexHits + "hit/" + typeIndexFallbacks + "miss"
            + " | processItems filtered=" + processItemsFiltered + "/" + processItemsTotal
            + " | skipSet=" + findAndReturnSkipSets);

        typeIndexHits = 0;
        typeIndexFallbacks = 0;
        findAndReturnSkipSets = 0;
        processItemsFiltered = 0;
        processItemsTotal = 0;
    }

    private OptiZombItems() {}
}

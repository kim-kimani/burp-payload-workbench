package com.cytonn.montoya.payloadextractor.ui.panels;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Pure, Swing/Montoya-free reordering algorithm shared by the Workbench's drag-to-reorder and its
 * move-up/move-down affordances. Operates on a flat list where items are logically grouped (e.g.
 * by container: which JSON object / which cookie jar an item belongs to) via a caller-supplied
 * group-key function - only items sharing a dragged item's group key are eligible to swap places
 * with it, and every other group's items keep their exact absolute slot positions untouched.
 *
 * <p>This is deliberately independent of {@code ParsedField} so it can be unit-tested with plain
 * objects (e.g. two-character strings like "A1"/"B2" where the first character is the group).
 */
public final class FieldReorderUtil {

    private FieldReorderUtil() {
    }

    /** Moves {@code dragged} to {@code newIndexWithinGroup} among the items sharing its group key. Absolute positions of other groups' items are unchanged. */
    public static <T, K> List<T> reorderWithinGroup(List<T> items, Function<T, K> groupKeyFn, T dragged, int newIndexWithinGroup) {
        K key = groupKeyFn.apply(dragged);

        List<Integer> groupPositions = new ArrayList<>();
        List<T> groupItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(groupKeyFn.apply(items.get(i)), key)) {
                groupPositions.add(i);
                groupItems.add(items.get(i));
            }
        }

        int fromIdx = groupItems.indexOf(dragged);
        if (fromIdx < 0) {
            return new ArrayList<>(items);
        }
        groupItems.remove(fromIdx);
        int clamped = Math.max(0, Math.min(newIndexWithinGroup, groupItems.size()));
        groupItems.add(clamped, dragged);

        List<T> result = new ArrayList<>(items);
        for (int i = 0; i < groupPositions.size(); i++) {
            result.set(groupPositions.get(i), groupItems.get(i));
        }
        return result;
    }

    public static <T, K> List<T> moveUpWithinGroup(List<T> items, Function<T, K> groupKeyFn, T item) {
        int idx = indexWithinGroup(items, groupKeyFn, item);
        return idx <= 0 ? new ArrayList<>(items) : reorderWithinGroup(items, groupKeyFn, item, idx - 1);
    }

    public static <T, K> List<T> moveDownWithinGroup(List<T> items, Function<T, K> groupKeyFn, T item) {
        int idx = indexWithinGroup(items, groupKeyFn, item);
        return reorderWithinGroup(items, groupKeyFn, item, idx + 1);
    }

    private static <T, K> int indexWithinGroup(List<T> items, Function<T, K> groupKeyFn, T item) {
        K key = groupKeyFn.apply(item);
        int idx = 0;
        for (T t : items) {
            if (Objects.equals(groupKeyFn.apply(t), key)) {
                if (t.equals(item)) {
                    return idx;
                }
                idx++;
            }
        }
        return -1;
    }
}

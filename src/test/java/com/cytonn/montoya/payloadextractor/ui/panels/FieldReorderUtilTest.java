package com.cytonn.montoya.payloadextractor.ui.panels;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-logic tests for the Workbench's drag/move-up/move-down algorithm - no Swing involved. */
class FieldReorderUtilTest {

    private static final Function<String, Character> GROUP_OF = s -> s.charAt(0);
    private static final List<String> ITEMS = List.of("A1", "A2", "A3", "B1", "B2");

    @Test
    void reorderMovesItemWithinItsOwnGroupOnly() {
        List<String> result = FieldReorderUtil.reorderWithinGroup(ITEMS, GROUP_OF, "A3", 0);
        assertEquals(List.of("A3", "A1", "A2", "B1", "B2"), result);
    }

    @Test
    void reorderInSecondGroupLeavesFirstGroupUntouched() {
        List<String> result = FieldReorderUtil.reorderWithinGroup(ITEMS, GROUP_OF, "B2", 0);
        assertEquals(List.of("A1", "A2", "A3", "B2", "B1"), result);
    }

    @Test
    void moveUpSwapsWithPreviousGroupMember() {
        List<String> result = FieldReorderUtil.moveUpWithinGroup(ITEMS, GROUP_OF, "A2");
        assertEquals(List.of("A2", "A1", "A3", "B1", "B2"), result);
    }

    @Test
    void moveDownSwapsWithNextGroupMember() {
        List<String> result = FieldReorderUtil.moveDownWithinGroup(ITEMS, GROUP_OF, "A1");
        assertEquals(List.of("A2", "A1", "A3", "B1", "B2"), result);
    }

    @Test
    void outOfRangeIndexClampsToGroupEnd() {
        List<String> result = FieldReorderUtil.reorderWithinGroup(ITEMS, GROUP_OF, "A1", 999);
        assertEquals(List.of("A2", "A3", "A1", "B1", "B2"), result);
    }

    @Test
    void moveUpAtTopOfGroupIsANoOp() {
        List<String> result = FieldReorderUtil.moveUpWithinGroup(ITEMS, GROUP_OF, "A1");
        assertEquals(ITEMS, result);
    }
}

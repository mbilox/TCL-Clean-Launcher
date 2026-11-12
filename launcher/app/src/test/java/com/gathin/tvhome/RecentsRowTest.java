package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Continue watching row: recently opened apps lead the first grid row, the rest stays alphabetical. */
public class RecentsRowTest {
    private static List<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Map<String, Long> opened(String pkg, long when) {
        Map<String, Long> map = new HashMap<>();
        map.put(pkg, when);
        return map;
    }

    @Test
    public void recentlyOpenedAppsLeadTheFirstRowNewestFirst() {
        Map<String, Long> opened = opened("b", 100L);
        opened.put("a", 50L);
        assertEquals(list("b", "a"), RecentsRow.firstRow(opened, 2));
    }

    /** The rule the request did not change: no history leaves the plain alphabetical grid. */
    @Test
    public void noHistoryKeepsTheAlphabeticalRule() {
        assertTrue(RecentsRow.firstRow(new HashMap<>(), 2).isEmpty());
    }

    @Test
    public void fewerRecentsThanOneRowStillLeaveTheRestAlphabetical() {
        Map<String, Long> opened = new HashMap<>();
        opened.put("d", 10L);
        opened.put("c", 20L);
        assertEquals(list("c", "d"), RecentsRow.firstRow(opened, 2));
    }

    @Test
    public void recentsBeyondOneRowFallBackToAlphabetical() {
        Map<String, Long> opened = opened("b", 100L);
        opened.put("a", 50L);
        opened.put("d", 10L);
        assertEquals(list("b", "a"), RecentsRow.firstRow(opened, 2));
    }

    @Test
    public void zeroOrNegativeRowSizeKeepsNothing() {
        assertTrue(RecentsRow.firstRow(opened("a", 5L), 0).isEmpty());
    }
}

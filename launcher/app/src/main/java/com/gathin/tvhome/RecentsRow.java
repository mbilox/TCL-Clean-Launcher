package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Continue watching row: the first row of the grid shows the apps opened most recently from
 * Home (LastOpened), the rest keep the alphabetical rule. Pure and unit-testable; AppAdapter maps
 * entries to packages and back.
 */
final class RecentsRow {

    private RecentsRow() {
    }

    /** Packages opened most recently, newest first, capped at {@code rowSize} (never negative). */
    static List<String> firstRow(Map<String, Long> opened, int rowSize) {
        List<String> row = new ArrayList<>();
        if (rowSize <= 0 || opened.isEmpty()) return row;
        List<String> candidates = new ArrayList<>(opened.keySet());
        // Newest first; List.sort is stable, so equal stamps keep whatever order the map had.
        candidates.sort((a, b) -> Long.compare(opened.get(b), opened.get(a)));
        for (String pkg : candidates) {
            if (row.size() == rowSize) break;
            row.add(pkg);
        }
        return row;
    }
}

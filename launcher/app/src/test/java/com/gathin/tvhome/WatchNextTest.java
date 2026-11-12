package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Continue watching content rows: only recent, published titles, newest first, capped at one row. */
public class WatchNextTest {
    private static final long DAY = 24L * 60 * 60 * 1000L;

    private static WatchNext.Item item(String title, long when) {
        return new WatchNext.Item(title, null, null, "https://example.test/" + title, when);
    }

    private static List<WatchNext.Item> items(WatchNext.Item... list) {
        return new ArrayList<>(Arrays.asList(list));
    }

    private static List<String> titles(List<WatchNext.Item> items) {
        List<String> out = new ArrayList<>();
        for (WatchNext.Item i : items) out.add(i.title);
        return out;
    }

    @Test
    public void rowsOlderThanSixtyDaysAreDropped() {
        long now = 1_000 * DAY;
        List<WatchNext.Item> out = WatchNext.recent(items(
                item("current", now - 1 * DAY),
                item("stale", now - 61 * DAY),
                item("old", now - 400 * DAY)), now);
        assertEquals(Arrays.asList("current"), titles(out));
    }

    @Test
    public void rowsWithoutAPositiveTimestampAreDropped() {
        long now = 1_000 * DAY;
        List<WatchNext.Item> out = WatchNext.recent(items(
                item("zero", 0L),
                item("negative", -5L),
                item("ok", now - DAY)), now);
        assertEquals(Arrays.asList("ok"), titles(out));
    }

    @Test
    public void newestFirst() {
        long now = 1_000 * DAY;
        List<WatchNext.Item> out = WatchNext.recent(items(
                item("older", now - 10 * DAY),
                item("newest", now - 1 * DAY),
                item("middle", now - 5 * DAY)), now);
        assertEquals(Arrays.asList("newest", "middle", "older"), titles(out));
    }

    @Test
    public void capsAtOneRow() {
        long now = 1_000 * DAY;
        List<WatchNext.Item> in = new ArrayList<>();
        for (int i = 0; i < 12; i++) in.add(item("t" + i, now - (12 - i) * DAY));
        List<WatchNext.Item> out = WatchNext.recent(in, now);
        assertEquals(WatchNext.MAX_ITEMS, out.size());
        assertEquals(Arrays.asList("t11", "t10", "t9", "t8", "t7", "t6", "t5", "t4"), titles(out));
    }

    /** Equal stamps keep the provider's order (List.sort is stable), so the row does not jitter between reloads. */
    @Test
    public void equalTimestampsKeepTheirProviderOrder() {
        long now = 1_000 * DAY;
        List<WatchNext.Item> out = WatchNext.recent(items(
                item("first", now - DAY),
                item("second", now - DAY)), now);
        assertEquals(Arrays.asList("first", "second"), titles(out));
    }

    @Test
    public void emptyInputStaysEmpty() {
        assertTrue(WatchNext.recent(items(), 1_000 * DAY).isEmpty());
    }
}

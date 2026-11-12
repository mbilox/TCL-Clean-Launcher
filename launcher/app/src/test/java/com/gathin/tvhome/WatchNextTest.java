package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Continue watching content rows: only recent, published titles, newest first, capped at one row. */
public class WatchNextTest {
    private static final long DAY = 24L * 60 * 60 * 1000L;

    private static WatchNext.Item item(String title, long when) {
        return item(title, "publisher.test", when);
    }

    private static WatchNext.Item item(String title, String publisher, long when) {
        return item(title, publisher, when, 0, 0);
    }

    private static WatchNext.Item item(String title, String publisher, long when, long position, long duration) {
        return new WatchNext.Item(title, null, null, "https://example.test/" + title, publisher,
                position, duration, when);
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

    /** The card's launch never reaches a resolver or browser: the publisher's package decides, or the card is dropped. */

    @Test
    public void linkThatNamesItsOwnPackageWins() {
        Object link = new Object();
        Object out = WatchNext.openable(link, "own.app", "publisher.test",
                pkg -> { throw new AssertionError("A self-named link must not be bound again"); },
                pkg -> { throw new AssertionError("A self-named link must not fall back"); });
        assertSame(link, out);
    }

    @Test
    public void rowWithoutPublisherKeepsTheRawLink() {
        Object link = new Object();
        Object out = WatchNext.openable(link, null, "",
                pkg -> { throw new AssertionError("Nothing to bind without a publisher"); },
                pkg -> { throw new AssertionError("Nothing to fall back without a publisher"); });
        assertSame(link, out);
    }

    @Test
    public void packagelessLinkTakesThePublisher() {
        Object link = new Object(), bound = new Object();
        Object out = WatchNext.openable(link, null, "publisher.test",
                pkg -> { assertEquals("publisher.test", pkg); return bound; },
                pkg -> { throw new AssertionError("The app launcher must not run when the link binds"); });
        assertSame(bound, out);
    }

    @Test
    public void linkThePublisherCannotHandleFallsBackToItsLauncher() {
        Object link = new Object(), appLaunch = new Object();
        Object out = WatchNext.openable(link, null, "publisher.test",
                pkg -> null,
                pkg -> { assertEquals("publisher.test", pkg); return appLaunch; });
        assertSame(appLaunch, out);
    }

    @Test
    public void applessRowDropsTheCard() {
        assertNull(WatchNext.openable(new Object(), null, "publisher.test",
                pkg -> null, pkg -> null));
    }

    /** The progress bar: only with a real position and duration, clamped to 0-100; -1 means no bar. */

    @Test
    public void progressNeedsBothPositionAndDuration() {
        assertEquals(-1, WatchNext.progress(0L, 90_000L));
        assertEquals(-1, WatchNext.progress(-1L, 90_000L));
        assertEquals(-1, WatchNext.progress(500L, 0L));
    }

    @Test
    public void progressIsPercentAndClamps() {
        assertEquals(42, WatchNext.progress(37_800L, 90_000L));
        assertEquals(100, WatchNext.progress(180_000L, 90_000L));
        assertEquals(1, WatchNext.progress(900L, 90_000L));
    }

    @Test
    public void nullLinkStaysNull() {
        assertNull(WatchNext.openable(null, null, "publisher.test",
                pkg -> { throw new AssertionError("Nothing to bind"); },
                pkg -> { throw new AssertionError("Nothing to fall back"); }));
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

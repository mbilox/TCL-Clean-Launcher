package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The Continue watching row is one full-width item in the grid, so the tiles under it sit one adapter
 * position further along than their row and column say. These are the rules that keep focus arithmetic on
 * the tile side of that shift.
 */
public class WatchRowTest {

    /** Where the row item goes: the first slot under the dock, and never past the tiles it follows. */

    @Test
    public void rowSitsOnTheFirstRowUnderTheDock() {
        assertEquals(4, WatchRow.position(4, 20, true));
        assertEquals(0, WatchRow.position(0, 20, true));
    }

    @Test
    public void rowAfterTheLastTileWhenThereAreFewerTilesThanItsRow() {
        assertEquals(2, WatchRow.position(4, 2, true));
        assertEquals(0, WatchRow.position(4, 0, true));
    }

    @Test
    public void noRowWhenTheProviderPublishesNothing() {
        assertEquals(-1, WatchRow.position(4, 20, false));
    }

    /** Adapter positions of the tiles: every tile at or after the row is one place further. */

    @Test
    public void tilesAfterTheRowShiftByOne() {
        assertEquals(0, WatchRow.adapterPosition(0, 4));
        assertEquals(3, WatchRow.adapterPosition(3, 4));
        assertEquals(5, WatchRow.adapterPosition(4, 4));
        assertEquals(12, WatchRow.adapterPosition(11, 4));
    }

    @Test
    public void withoutARowAdapterPositionsAreTileIndices() {
        assertEquals(6, WatchRow.adapterPosition(6, -1));
        assertEquals(6, WatchRow.tileIndex(6, -1));
    }

    @Test
    public void tileIndexIsTheInverseOfTheAdapterPosition() {
        for (int index = 0; index < 16; index++) {
            assertEquals(index, WatchRow.tileIndex(WatchRow.adapterPosition(index, 4), 4));
        }
        // The row's own position belongs to no tile: it maps to the tile folded onto it.
        assertEquals(4, WatchRow.tileIndex(4, 4));
    }

    /** Which row of tiles sits directly above or below the row, for Up and Down. */

    @Test
    public void rowIsBelowTheDockRow() {
        for (int index = 0; index < 4; index++) {
            assertTrue("tile " + index, WatchRow.rowBelow(index, 4, 4));
            assertFalse("tile " + index, WatchRow.rowAbove(index, 4, 4));
        }
        assertFalse(WatchRow.rowBelow(4, 4, 4));
        assertTrue(WatchRow.rowAbove(4, 4, 4));
    }

    @Test
    public void onlyTheFirstRowUnderTheRowIsAboveIt() {
        for (int index = 4; index < 8; index++) assertTrue("tile " + index, WatchRow.rowAbove(index, 4, 4));
        assertFalse(WatchRow.rowAbove(8, 4, 4));
        assertFalse(WatchRow.rowBelow(8, 4, 4));
    }

    @Test
    public void noRowBesideAnythingWithoutARow() {
        assertFalse(WatchRow.rowAbove(4, -1, 4));
        assertFalse(WatchRow.rowBelow(0, -1, 4));
    }

    @Test
    public void aRowAfterTheLastTileHangsBelowEveryTile() {
        assertTrue(WatchRow.rowBelow(0, 2, 4));
        assertTrue(WatchRow.rowBelow(1, 2, 4));
        assertFalse(WatchRow.rowBelow(2, 2, 4));
    }

    /** How much to scroll so the row item (cards plus the caption under them) is fully on screen. */

    @Test
    public void noScrollWhenTheRowIsFullyVisible() {
        assertEquals(0, WatchRow.scrollDeficit(100, 400, 0, 1000, 0));
    }

    @Test
    public void scrollDownByTheCutAmountWhenTheRowBottomIsBelowTheScreen() {
        // Row bottom 1100 on a viewport ending at 1000: scroll down 100 to reveal the caption.
        assertEquals(100, WatchRow.scrollDeficit(500, 1100, 0, 1000, 0));
    }

    @Test
    public void thePadCountsWhenTheBottomNeedsIt() {
        // Row bottom 995 fits, but the focused card's zoom needs 20px more: scroll down 15.
        assertEquals(15, WatchRow.scrollDeficit(500, 995, 0, 1000, 20));
        // The pad is not free room: a row that already clears its pad does not scroll.
        assertEquals(0, WatchRow.scrollDeficit(500, 950, 0, 1000, 20));
    }

    @Test
    public void scrollUpWhenTheRowTopIsAboveTheScreen() {
        assertEquals(-50, WatchRow.scrollDeficit(-50, 300, 0, 1000, 0));
    }

    @Test
    public void noScrollWhenARowTallerThanTheViewport() {
        // Nothing sane to do; the deficit must not oscillate or scroll at all.
        assertEquals(0, WatchRow.scrollDeficit(0, 2000, 0, 1000, 0));
    }

    /** Vertical navigation keeps the column: the row's cards map onto the dock and the rows under it. */

    @Test
    public void upFromTheRowKeepsTheCardsColumnInTheDock() {
        assertEquals(0, WatchRow.dockTarget(0, 4));
        assertEquals(2, WatchRow.dockTarget(2, 4));
    }

    @Test
    public void aCardPastTheDockLandsOnItsLastFilledTile() {
        // The dock holds 4 apps; a card at column 5 has no dock tile under it: the last filled one takes it.
        assertEquals(3, WatchRow.dockTarget(5, 4));
    }

    @Test
    public void anEmptyDockHasNoTarget() {
        assertEquals(-1, WatchRow.dockTarget(5, 0));
    }

    @Test
    public void downFromTheRowKeepsTheCardsColumnInTheRowBelow() {
        // The first tile under the row is the row-below column 0; the card's column shifts onto it.
        assertEquals(10, WatchRow.rowDownTarget(10, 0, 20));
        assertEquals(12, WatchRow.rowDownTarget(10, 2, 20));
    }

    @Test
    public void downFromTheRowClampsAtTheLastTile() {
        // A short last row: the column would fall past the end, so the last tile takes the focus.
        assertEquals(19, WatchRow.rowDownTarget(18, 5, 20));
        assertEquals(11, WatchRow.rowDownTarget(10, 2, 12));
    }
}

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
}

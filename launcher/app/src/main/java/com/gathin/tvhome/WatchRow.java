package com.gathin.tvhome;

/**
 * Position arithmetic for the Continue watching row. The row is one full-width item in a grid that otherwise
 * holds one tile per span, so every tile under it sits one adapter position further along than its row and
 * column say. The grid's own key arithmetic stays on the tile side of the shift; these are the rules.
 *
 * <p>{@code rowPosition} is the row's adapter position, or -1 when it is not shown.
 */
final class WatchRow {
    private WatchRow() { }

    /**
     * The adapter position the row takes: the first slot of the first row under the dock band, and never past
     * the tiles that follow it (with a handful of apps the row still shows, after the last row).
     */
    static int position(int firstRowUnderDock, int tiles, boolean shown) {
        if (!shown) return -1;
        return Math.max(0, Math.min(firstRowUnderDock, tiles));
    }

    /** The adapter position of the tile at {@code tileIndex}. */
    static int adapterPosition(int tileIndex, int rowPosition) {
        return rowPosition >= 0 && tileIndex >= rowPosition ? tileIndex + 1 : tileIndex;
    }

    /** The tile at an adapter position; the row's own position folds onto the tile it hides. */
    static int tileIndex(int adapterPosition, int rowPosition) {
        return rowPosition >= 0 && adapterPosition > rowPosition ? adapterPosition - 1 : adapterPosition;
    }

    /** True when the row's own grid row is directly above the tile at {@code tileIndex}. */
    static boolean rowAbove(int tileIndex, int rowPosition, int columns) {
        if (rowPosition < 0 || columns <= 0) return false;
        return tileIndex >= rowPosition && tileIndex < rowPosition + columns;
    }

    /** True when the row's own grid row is directly below the tile at {@code tileIndex}. */
    static boolean rowBelow(int tileIndex, int rowPosition, int columns) {
        if (rowPosition < 0 || columns <= 0) return false;
        return tileIndex >= rowPosition - columns && tileIndex < rowPosition;
    }
}

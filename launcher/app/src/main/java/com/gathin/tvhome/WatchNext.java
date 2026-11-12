package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Continue watching row's content cards: what the TV's own provider says the owner was watching.
 * Android publishes those rows through TvProvider ({@code content://android.media.tv/watch_next_program}),
 * which needs the READ_TV_LISTINGS permission. Without it, or with nothing published, the query fails and
 * the row falls back to the app recents it already showed. The provider column names are the platform's,
 * spelled out here so the launcher needs no androidx.tvprovider dependency.
 */
final class WatchNext {

    /** One row: the follow row shows at most this many. */
    static final int MAX_ITEMS = 8;
    /** Rows older than this are stale (the owner's unit still has 2022 "Flow" rows published). */
    static final long MAX_AGE = 60L * 24 * 60 * 60 * 1000;

    // Spelled out as a string, not a Uri: the class must load in a plain JVM test (WatchNextTest) too.
    private static final String WATCH_NEXT = "content://android.media.tv/watch_next_program";
    private static final String[] COLUMNS = {
            "title", "episode_title", "poster_art_uri", "intent_uri", "last_engagement_time_utc_millis",
            "package_name"};

    private WatchNext() {
    }

    /** A dumb holder: one published row, already split into what the tile needs. */
    static final class Item {
        final String title;
        final String episodeTitle;
        final String posterUri;
        final String intentUri;
        final long lastEngagement;
        /** The app that published the row: the card binds its deep link to it, never to a resolver. */
        final String packageName;

        Item(String title, String episodeTitle, String posterUri, String intentUri,
             String packageName, long lastEngagement) {
            this.title = title;
            this.episodeTitle = episodeTitle;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.packageName = packageName;
            this.lastEngagement = lastEngagement;
        }
    }

    /**
     * The rows to show, newest first, capped at {@link #MAX_ITEMS}; empty when the provider cannot be read
     * (no READ_TV_LISTINGS, no TvProvider, nothing published): the row then keeps its app recents only.
     */
    static List<Item> load(Context ctx, long now) {
        ContentResolver resolver = ctx.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(Uri.parse(WATCH_NEXT), COLUMNS, null, null, null);
            if (cursor == null) return new ArrayList<>();
            List<Item> found = new ArrayList<>();
            while (cursor.moveToNext()) {
                String intentUri = cursor.getString(3);
                // A row with no way to open it is not a card.
                if (intentUri == null || intentUri.isEmpty()) continue;
                String title = cursor.getString(0);
                found.add(new Item(title == null ? "" : title, cursor.getString(1), cursor.getString(2),
                        intentUri, cursor.getString(5), cursor.getLong(4)));
            }
            return recent(found, now);
        } catch (Exception ignored) {
            // SecurityException without the permission, IllegalArgumentException when no provider answers,
            // and anything else a vendor's TvProvider throws: the row degrades, Home still starts.
            return new ArrayList<>();
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Pure rule for what a content card opens: the resolver and a random browser are never the answer.
     * A deep link that names its own package opens as published; a package-less link is bound to the
     * row's publisher through {@code bound} (the caller checks that app can take the link); when it
     * cannot, the app's own launcher through {@code launcher}; with neither, the card is dropped (null):
     * an unusable link must not fall into whichever browser a resolver picks.
     *
     * @param <L> the launch type (an Intent in production; anything in the plain JVM test)
     */
    static <L> L openable(L link, String linkPackage, String publisher,
            Function<String, L> bound, Function<String, L> launcher) {
        if (link == null) return null;
        if (publisher == null || publisher.isEmpty()) return link;
        if (linkPackage != null && !linkPackage.isEmpty()) return link;
        L result = bound.apply(publisher);
        return result != null ? result : launcher.apply(publisher);
    }

    /**
     * Pure rule for what the row shows: drop rows with no usable timestamp and rows last watched longer ago
     * than {@link #MAX_AGE}, newest first, capped at {@link #MAX_ITEMS}. {@link List#sort} is stable, so rows
     * published with the same stamp keep the provider's order and the row does not jitter between reloads.
     */
    static List<Item> recent(List<Item> items, long now) {
        List<Item> kept = new ArrayList<>();
        if (items == null) return kept;
        for (Item item : items) {
            if (item == null || item.lastEngagement <= 0) continue;
            if (now - item.lastEngagement > MAX_AGE) continue;
            kept.add(item);
        }
        kept.sort((a, b) -> Long.compare(b.lastEngagement, a.lastEngagement));
        if (kept.size() > MAX_ITEMS) return new ArrayList<>(kept.subList(0, MAX_ITEMS));
        return kept;
    }
}

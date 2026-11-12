package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    /** A successful load is snapshotted; the snapshot feeds the row for this long (see load's catch). */
    static final long SNAPSHOT_AGE = 7L * 24 * 60 * 60 * 1000;
    /** Diagnostics: the row's own tag shares AppAdapter's, so one filter sees the whole story. */
    static final String TAG = "WatchRowDebug";

    // Spelled out as a string, not a Uri: the class must load in a plain JVM test (WatchNextTest) too.
    private static final String WATCH_NEXT = "content://android.media.tv/watch_next_program";
    private static final String[] COLUMNS = {
            "title", "episode_title", "poster_art_uri", "intent_uri", "last_engagement_time_utc_millis",
            "package_name", "last_watched_position_millis", "duration_millis"};

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
        /** Playback state the row publishes: the card's progress bar uses both; -1/0 means no bar. */
        final long positionMillis;
        final long durationMillis;

        Item(String title, String episodeTitle, String posterUri, String intentUri,
             String packageName, long positionMillis, long durationMillis, long lastEngagement) {
            this.title = title;
            this.episodeTitle = episodeTitle;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.packageName = packageName;
            this.positionMillis = positionMillis;
            this.durationMillis = durationMillis;
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
                        intentUri, cursor.getString(5), cursor.getLong(6), cursor.getLong(7),
                        cursor.getLong(4)));
            }
            List<Item> recent = recent(found, now);
            if (!recent.isEmpty()) saveSnapshot(ctx, recent, now);   // the provider worked: remember it
            return recent;
        } catch (Exception e) {
            // SecurityException without the permission, IllegalArgumentException when no provider answers,
            // and anything else a vendor's TvProvider throws: the row degrades, Home still starts.
            List<Item> snapshot = loadSnapshot(ctx, now);
            if (!snapshot.isEmpty()) Log.i(TAG, "provider read FAILED (" + e + "): serving "
                    + snapshot.size() + " snapshot rows");
            return snapshot;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** The snapshot file: rows the launcher itself saw, so a broken provider (firmware schema bug)
     *  still shows what was last watched. Writes are best-effort and never throw into load(). */
    private static File snapshotFile(Context ctx) {
        return new File(ctx.getFilesDir(), "watch_next_snapshot.json");
    }

    private static void saveSnapshot(Context ctx, List<Item> items, long now) {
        try {
            JSONArray array = new JSONArray();
            for (Item item : items) {
                JSONObject row = new JSONObject();
                row.put("title", item.title);
                row.put("episode", item.episodeTitle == null ? "" : item.episodeTitle);
                row.put("poster", item.posterUri == null ? "" : item.posterUri);
                row.put("link", item.intentUri);
                row.put("pkg", item.packageName == null ? "" : item.packageName);
                row.put("pos", item.positionMillis);
                row.put("dur", item.durationMillis);
                row.put("when", item.lastEngagement);
                array.put(row);
            }
            JSONObject snapshot = new JSONObject();
            snapshot.put("savedAt", now);
            snapshot.put("rows", array);
            try (FileOutputStream out = new FileOutputStream(snapshotFile(ctx))) {
                out.write(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException | IOException | RuntimeException e) {
            Log.i(TAG, "snapshot save FAILED: " + e);
        }
    }

    /** The last snapshot the launcher itself recorded, no older than {@link #SNAPSHOT_AGE}; empty list
     *  when there is none (or it is too old): the row then shows nothing, as without a provider. */
    static List<Item> loadSnapshot(Context ctx, long now) {
        List<Item> items = new ArrayList<>();
        try (InputStream in = new FileInputStream(snapshotFile(ctx))) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) >= 0) buffer.write(chunk, 0, read);
            JSONObject snapshot = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            long savedAt = snapshot.getLong("savedAt");
            if (now - savedAt > SNAPSHOT_AGE) return items;
            JSONArray rows = snapshot.getJSONArray("rows");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                items.add(new Item(row.optString("title"), row.optString("episode"),
                        row.optString("poster"), row.optString("link"), row.optString("pkg"),
                        row.optLong("pos"), row.optLong("dur"), row.optLong("when")));
            }
        } catch (IOException | JSONException | RuntimeException e) {
            // No snapshot (first run, cleared storage, corrupt file): the row simply stays empty.
        }
        return items;
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
     * Pure rule for the card's progress bar: percent of the row's playback, clamped to 0-100; -1 when
     * either side is missing (a row with no position published, or nothing left to progress).
     */
    static int progress(long positionMillis, long durationMillis) {
        if (positionMillis <= 0 || durationMillis <= 0) return -1;
        long percent = positionMillis * 100 / durationMillis;
        return percent > 100 ? 100 : (int) percent;
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

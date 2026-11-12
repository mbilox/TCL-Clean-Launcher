package com.gathin.tvhome;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * One-shot diagnostic, started over adb: dumps the TV provider's watch-next and preview rows to
 * logcat, so the owner's unit answers whether streaming apps publish continue-watching data.
 * Not reachable from the UI; keep until the watch-next row decides, then remove.
 */
public final class WatchNextProbeActivity extends Activity {
    private static final String TAG = "WatchNextProbe";
    private static final String[] PROJECTION = {"_id", "title", "poster_art_uri",
            "playback_position_millis", "duration_millis", "last_engagement_time_utc_millis", "intent_uri"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dump("content://android.media.tv/watch_next_program");
        dump("content://android.media.tv/preview_program");
        finish();
    }

    private void dump(String uri) {
        Log.i(TAG, "=== " + uri);
        try (Cursor c = getContentResolver().query(Uri.parse(uri), PROJECTION, null, null, null)) {
            if (c == null) {
                Log.i(TAG, "cursor null (no provider)");
                return;
            }
            Log.i(TAG, "rows: " + c.getCount());
            while (c.moveToNext()) {
                Log.i(TAG, c.getString(0) + " | " + c.getString(1) + " | poster=" + c.getString(2)
                        + " | pos=" + c.getString(3) + "/" + c.getString(4)
                        + " | last=" + c.getString(5) + " | " + c.getString(6));
            }
        } catch (Exception e) {
            Log.i(TAG, "query failed: " + e);
        }
    }
}

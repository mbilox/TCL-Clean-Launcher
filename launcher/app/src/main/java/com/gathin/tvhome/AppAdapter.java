package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order,
 * then empty slots up to a full row, see {@link DockSlots}), then the module cards the owner chose to show
 * (by label), the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        void onLibraryRequested();
        void onLibraryFocused();
        /** Hold OK on a tile: the activity shows the tile menu (move, remove from Home, app info). */
        void onTileLongPress(int position, View card);
        void onLibraryUp();
        /** Focus landed on a card of the Continue watching row: it is no grid tile, so nothing tracks it there. */
        void onWatchRowFocused();
        /** Up from that row (focused card {@code cardIndex}): back to the dock, or the top bar when the row is the first line on screen. */
        void onWatchRowUp(int cardIndex);
        /** Down from that row (focused card {@code cardIndex}): the first grid row under it, or the App library when nothing follows. */
        void onWatchRowDown(int cardIndex);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, EMPTY = 3;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        /** Posters land off the main thread: mutable, the tile rebinds when its art arrives. */
        Drawable banner;
        final Drawable icon;
        /**
         * Continue watching content card: the published deep link its tile opens. null is an ordinary tile,
         * which launches its component through {@link #launch(Entry)}; such entries are not app tiles, so
         * they never enter the recents map and never count as docked.
         */
        final Intent launch;
        /** When the content card was last watched (0 for app tiles): the follow row's order across reloads. */
        final long lastEngagement;
        /** The content card's art location, fetched off the main thread when it is an https URL. */
        final String posterUri;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** A content card's two-line caption (title, then the app's small icon + name); null on plain tiles. */
        CharSequence detail;
        /** The card's app name: the badge line is rebuilt with it when the title line must be cut. */
        String appName;
        /** Percent of the row's playback (0-100 from the provider); 0 means no progress bar on the poster. */
        int progressPct;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;
        /** uniform_tiles card fill, top and bottom colour; null until sampled from the icon. */
        int[] backdrop;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this(kind, label, component, banner, icon, caption, accent, null, 0L);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent,
              Intent launch, long lastEngagement) {
            this(kind, label, component, banner, icon, caption, accent, launch, lastEngagement, null);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent,
              Intent launch, long lastEngagement, String posterUri) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.launch = launch;
            this.lastEngagement = lastEngagement;
            this.posterUri = posterUri;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    /** Package the follow row gives a content card whose deep link names no installed app. */
    static final String WATCH_NEXT_PKG = "watch_next";
    /** Widest poster a content card decodes at: the tile is smaller, the rest would only cost native heap. */
    static final int POSTER_MAX_W = 480;

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return itemHeightPx(tileHeightPx, true, density);
    }

    /** The same for either layout: the plain grid (dock_row false) uses the compact spacing. */
    static int itemHeightPx(int tileHeightPx, boolean dockRow, float density) {
        return tileHeightPx + Math.round((dockRow ? ITEM_EXTRA_DP : COMPACT_ITEM_EXTRA_DP) * density);
    }

    /**
     * dock_row false: 4dp padding above and below, a 4dp gap and a 20dp name label, and 16dp kept under the last
     * row. The plain grid fits more rows on the screen, and with item_app.xml's roomier spacing the tiles came
     * out small with wide gaps between them.
     */
    static final float COMPACT_ITEM_EXTRA_DP = 4 + 4 + 4 + 20;
    static final float COMPACT_BOTTOM_ROOM_DP = 16;

    /** item_app.xml: 8dp padding above and below, a 7dp gap and the 22dp name label under the tile. */
    static final float ITEM_EXTRA_DP = 45;
    /** DockDecoration's offsets above and below the dock row. */
    static final float DOCK_EXTRA_DP = 6 + 12;
    /** Room kept under the last row rows_below_dock asks for (MainActivity places the dock with the same). */
    static final float BOTTOM_ROOM_DP = 30;
    /** Room kept above the dock for the dock title (48sp hero name plus its 40dp gap) when dock_title is on. */
    static final float TITLE_ROOM_DP = 104;
    /** dock_row false: room above the first row for its zoom and glass (MainActivity places the grid with it). */
    static final float TOP_ROOM_DP = 12;

    /**
     * Tile width that fits the columns across and, with rows_below_dock > 0, the dock (with its title above it,
     * if shown) plus that many rows down the grid's height; the smaller wins. With rows_below_dock = 0, or before
     * the grid has a height, only the width counts, as before. Without this, 2 or 3 rows below the dock did not
     * fit: the dock was pushed to the top and its title drew over the clock. With dock_row false there is no band
     * or title to make room for: the first row plus rows_below_dock rows fit under a small top margin.
     */
    static int tileWidthPx(int innerWidthPx, int gridHeightPx, int columns, int rowsBelowDock, boolean dockTitle,
                           boolean dockRow, float density) {
        int w = tileWidthPx(innerWidthPx, columns, density);
        if (gridHeightPx <= 0 || rowsBelowDock <= 0) return w;
        float reserved = (dockRow ? DOCK_EXTRA_DP + BOTTOM_ROOM_DP + (dockTitle ? TITLE_ROOM_DP : 0)
                : TOP_ROOM_DP + COMPACT_BOTTOM_ROOM_DP) * density;
        float perItem = (gridHeightPx - reserved) / (rowsBelowDock + 1);
        float tileH = perItem - (dockRow ? ITEM_EXTRA_DP : COMPACT_ITEM_EXTRA_DP) * density;
        int byHeight = Math.round(tileH * DESIGN_W_DP / DESIGN_H_DP);
        // Shrink at most to MIN_HEIGHT_FIT of the width-only size. Below that the tiles look lost in their wide
        // columns (2 rows under a titled dock came out at 80dp); the rows that still don't fit run off the bottom
        // and scroll, and MainActivity keeps the title's room above the dock.
        return Math.max(Math.max(Math.round(80 * density), Math.round(w * MIN_HEIGHT_FIT)), Math.min(w, byHeight));
    }

    /** The smallest share of the width-only tile size that fitting rows_below_dock into the height may go to. */
    static final float MIN_HEIGHT_FIT = 0.85f;

    private final Context ctx;
    private final int rowsBelowDock;
    private final boolean dockTitle;
    /** The owner's saved order (reorder button, Order): key -> rank. Empty when there is none or it no longer applies. */
    private Map<String, Integer> savedRanks = Collections.emptyMap();
    /** Current Continue watching row: its packages, newest first, and when each was opened (read per reload). */
    private List<String> followRowPkgs = Collections.emptyList();
    private Map<String, Long> openedByPkg = Collections.emptyMap();
    /** Poster art the watch-next cards decoded, keyed by its URI: a rebuild must not refetch the network. */
    private static final Map<String, Drawable> POSTERS = Collections.synchronizedMap(
            new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Drawable> eldest) {
                    return size() > 32;
                }
            });
    /** Posters land off the main thread; one worker is enough for a row's worth of art. */
    private final java.util.concurrent.ExecutorService posterPool = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Dock size recorded with the saved order (Order.docked), or -1: then dock_apps says which tiles are docked. */
    private int savedDocked = -1;
    /** Reorder mode: no empty dock slots, so every position holds a tile that can move. */
    private boolean reordering;
    private final TileListener listener;
    private final int dockSize;
    /** config dock_row: false makes the dock apps the first tiles of one plain grid (names shown, no empty slots). */
    private final boolean dockRow;
    /** config single_row: tiles sit in one sideways row, each as wide as a grid column would be. */
    private final boolean singleRow;
    /** config always_show_names: the alpha of an unfocused tile's name (0 hides it). */
    private final float restNameAlpha;
    /** cinematic_home: only then does a launch record the last-opened app the shelf uses. */
    private final boolean cinematic;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock). */
    private final boolean fillDock;
    /** Dock apps found at the last reload, and the empty slots after them. */
    private int docked, emptySlots;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    /** Apps pre-seeded into the App library's "added" set (config.xml library_apps); empty by default. */
    private final Set<String> libraryPreload;
    private final boolean animations;
    private final boolean advancedEffects;
    private final boolean showLibrary;
    /** Packages with a leanback entry at the last reload(): their tiles launch with the leanback category. */
    private Set<String> leanbackPackages = new HashSet<>();
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    /** The focus zoom may overshoot (config.xml, focus_overshoot). */
    private final boolean focusOvershoot;
    /** Package to the action its tile sends instead of the start screen (config.xml, launch_actions). */
    private final Map<String, String> launchActions;
    /** Hold OK opens the activity's tile menu (config.xml, tile_menu); false opens App info, as before. */
    private final boolean tileMenu;
    /** Tile names and bundled banners that replace the app's own (config.xml, app_labels and app_banners). */
    private final Map<String, String> appLabels, appBanners;
    /** Every app tile is its icon on a card tinted from that icon, never its banner (config.xml, uniform_tiles). */
    private final boolean uniformTiles;
    /** App banners decoded in RGB_565 instead of ARGB_8888 (config.xml banner_565): half the native heap. */
    private final boolean banner565;
    /** Cap for the density a banner decodes at, 0 keeps the default (config.xml banner_density). */
    private final int bannerMaxDensity;
    /** Continue watching: the first grid row shows the apps opened most recently from Home (config follow_row). */
    private final boolean followRow;
    /** Continue watching: that row starts with the content the owner was watching (settings watch_next). */
    private final boolean watchNext;
    /** Packages drawn from their icon and name even when they have a banner (config.xml icon_tiles). */
    private final Set<String> iconTiles;
    /**
     * One outline provider for every tile: the shape only reads the view's width, so building an anonymous
     * provider per holder (upstream #446) was an allocation per tile for the same result.
     */
    private final ViewOutlineProvider cardOutline;
    private final List<Entry> items = new ArrayList<>();
    /**
     * The Continue watching cards, newest first: the sideways row draws them instead of the grid (the single
     * row keeps them as ordinary tiles, where a nested sideways row has nowhere to go).
     */
    private final List<Entry> contentCards = new ArrayList<>();
    /** Adapter position of that sideways row, or -1 when it is not shown. See {@link WatchRow}. */
    private int watchRowPos = -1;

    /** getItemViewType: a grid tile, the App library row, the Continue watching row. */
    static final int TYPE_TILE = 0, TYPE_LIBRARY = 1, TYPE_WATCH_ROW = 2;
    /** Gap between two cards of the sideways row: the slot is wider than the card and the card stays centred. */
    static final float ROW_CARD_GAP_DP = 12f;

    /**
     * The sideways row's cards report through the same {@link TileListener} as a tile: their position is the
     * card's place in the row, and Up/Down leave the row instead of running through the grid.
     */
    private final TileListener rowTiles = new TileListener() {
        @Override public void onTileFocused(int index, View card) {
            listener.onWatchRowFocused();
        }

        @Override public boolean onTileVerticalKey(int index, int keyCode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                listener.onWatchRowUp(index);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                listener.onWatchRowDown(index);
                return true;
            }
            // Left/Right walk the cards, the row scrolling the focused one in; only its two ends are walled off.
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? index >= contentCards.size() - 1 : index == 0;
        }

        @Override public void onLibraryRequested() { }
        @Override public void onLibraryFocused() { }
        @Override public void onTileLongPress(int index, View card) { }
        @Override public void onLibraryUp() { }
        @Override public void onWatchRowFocused() { }
        @Override public void onWatchRowUp(int cardIndex) { }
        @Override public void onWatchRowDown(int cardIndex) { }
    };

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.fillDock = cfg.fillDock;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.libraryPreload = new HashSet<>(cfg.libraryApps);
        this.animations = cfg.animations;
        this.advancedEffects = cfg.animations && cfg.advancedEffects;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.showLibrary = LibraryApps.showPill(cfg.appLibrary, cfg.libraryButton, cfg.showLibraryPill);
        this.reducedMotion = cfg.reducedMotion;
        this.focusOvershoot = cfg.focusOvershoot;
        this.rowsBelowDock = cfg.rowsBelowDock;
        this.dockTitle = cfg.dockTitle;
        this.dockRow = cfg.dockRow;
        this.singleRow = cfg.singleRow;
        this.restNameAlpha = cfg.alwaysShowNames ? 0.75f : 0f;
        this.cinematic = cfg.cinematicHome;
        this.launchActions = cfg.launchActions;
        this.iconTiles = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.icon_tiles)));
        this.tileMenu = cfg.tileMenu;
        this.appLabels = cfg.appLabels;
        this.appBanners = cfg.appBanners;
        this.uniformTiles = cfg.uniformTiles;
        this.banner565 = cfg.banner565;
        this.bannerMaxDensity = cfg.bannerMaxDensity;
        this.followRow = cfg.followRow;
        this.watchNext = cfg.watchNext;
        final float density = ctx.getResources().getDisplayMetrics().density;
        this.cardOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
            }
        };
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    void reload(List<Cards.Card> cards, List<ResolveInfo> leanback) {
        PackageManager pm = ctx.getPackageManager();
        // The LEANBACK query is shared with the module-card check (MainActivity); copy it before adding
        // library apps so the caller's list stays as it was.
        List<ResolveInfo> found = new ArrayList<>(leanback);
        Set<String> tvPackages = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) tvPackages.add(ri.activityInfo.packageName);
        leanbackPackages = tvPackages;
        // Copy before merging: LibraryApps.load() may return an immutable empty set (#419).
        Set<String> added = new HashSet<>(LibraryApps.load(ctx));
        added.addAll(libraryPreload);
        Set<String> removed = LibraryApps.loadRemoved(ctx);
        if (!added.isEmpty()) {
            Set<String> seen = new HashSet<>(tvPackages);
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                if (ri.activityInfo != null && added.contains(ri.activityInfo.packageName)
                        && seen.add(ri.activityInfo.packageName)) found.add(ri);
            }
        }
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        // Some system apps declare two leanback entry points under one name (Sony's Timers & Clock on the
        // BRAVIA VU31), which would show as two identical tiles: keep the first per package and label.
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !ri.activityInfo.exported
                    || !LibraryApps.visible(tvPackages.contains(pkg), hidden.contains(pkg), added.contains(pkg), removed.contains(pkg))) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
            // redraw the tile name as something else (#117).
            String label = card != null ? card.label : Cards.cleanLabel(
                    appLabels.containsKey(pkg) ? appLabels.get(pkg) : String.valueOf(ri.loadLabel(pm)), pkg);
            if (!seen.add(pkg + '\n' + label)) continue;
            Drawable banner = null;
            // uniform_tiles draws the icon, so an app's banner is never loaded. A module card keeps its banner.
            if (!(uniformTiles && card == null)) {
                banner = bundledBanner(appBanners.get(pkg));
                if (banner == null) banner = iconTiles.contains(pkg) ? iconBanner(ri.loadIcon(pm)) : loadBanner(pm, ri);
            }
            // The icon is only drawn (and sampled for the glow) when there is no banner, so a tile with a
            // banner skips loading and decoding it.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, label, component, banner, icon, card.caption, card.accent));
            } else {
                fresh.add(new Entry(label, component, banner, icon));
            }
        }
        // Continue watching: sideways in its own row when the grid has columns, inline in the single row.
        contentCards.clear();
        if (followRow && watchNext) {
            List<Entry> content = contentEntries();
            if (singleRow) {
                // Inline: the content cards are ordinary entries, so the follow row places them with the app tiles.
                fresh.addAll(content);
                fetchMissingPosters(content);
            } else {
                // Newest first; the sort is stable, so equal stamps keep the provider's order.
                content.sort((x, y) -> Long.compare(y.lastEngagement, x.lastEngagement));
                contentCards.addAll(content);
                fetchMissingPosters(contentCards);
            }
        }
        refreshFollowRow(fresh);
        Collections.sort(fresh, (a, b) -> {
            // The owner's own order first; tiles it does not know (new installs) follow by the rule below.
            int saved = Order.compare(savedRanks, a.component.flattenToShortString(), b.component.flattenToShortString());
            if (saved != 0) return saved;
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Retain the optional Default-home tile until dependent tile-ordering work is merged.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        docked = 0;
        while (docked < fresh.size() && isDocked(fresh.get(docked))) docked++;
        emptySlots = dockRow && !reordering ? DockSlots.empty(docked, dockSize, fresh.size(), fillDock) : 0;
        for (int i = 0; i < emptySlots; i++) {
            fresh.add(docked + i, new Entry(Entry.EMPTY, "", new ComponentName(ctx, "empty-dock-slot-" + i),
                    null, null, null, 0));
        }
        watchRowPos = -1;
        if (followRow) {
            int start = followRowStart();
            if (singleRow) {
                // Content cards take the first slots of the row, app recents fill the ones left over.
                int content = placeContentRow(fresh, start, dockSize);
                placeFollowRow(fresh, start + content, openedByPkg, dockSize - content);
            } else {
                // The sideways row is one full-width item where the cards used to be, and the app recents keep
                // the grid row under it.
                watchRowPos = WatchRow.position(start, fresh.size(), !contentCards.isEmpty());
                placeFollowRow(fresh, start, openedByPkg, dockSize);
            }
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    /** When each follow-row candidate (app tiles outside the dock and the owner's saved order) was opened. */
    private Map<String, Long> readOpened(List<Entry> tiles) {
        Map<String, Long> opened = new HashMap<>();
        if (!followRow) return opened;
        for (Entry e : tiles) {
            // Content cards are not app tiles: their package must not collide with that app's own tile.
            if (e.launch != null) continue;
            if (e.kind != Entry.APP || favourites.contains(e.pkg)) continue;
            if (savedRanks.containsKey(e.component.flattenToShortString())) continue;
            long when = LastOpened.get(ctx, e.pkg);
            if (when > 0) opened.put(e.pkg, when);
        }
        return opened;
    }

    /** Rebuilds the follow-row state for {@code tiles}; returns whether the row changed. */
    private boolean refreshFollowRow(List<Entry> tiles) {
        openedByPkg = readOpened(tiles);
        List<String> next = RecentsRow.firstRow(openedByPkg, dockSize);
        boolean changed = !next.equals(followRowPkgs);
        followRowPkgs = next;
        return changed;
    }

    /** The first row below the dock: the next row boundary after the dock block and its empty slots. */
    private int followRowStart() {
        return ((docked + emptySlots + dockSize - 1) / dockSize) * dockSize;
    }

    /**
     * Moves the Continue watching content cards to the row at {@code start}, newest first, and returns how many
     * it placed so the app tiles can fill the slots after them. The dock band keeps the first {@code start}
     * non-card tiles of the base order (favourites, then the module cards and grid tiles that fill it) — a
     * content card never takes a band slot, whatever the alphabetical order would have said.
     */
    private static int placeContentRow(List<Entry> tiles, int start, int rowSize) {
        if (start >= tiles.size() || rowSize <= 0) return 0;
        List<Entry> band = new ArrayList<>(), cards = new ArrayList<>(), rest = new ArrayList<>();
        for (Entry e : tiles) {
            if (e.launch != null) cards.add(e);
            else if (band.size() < start) band.add(e);
            else rest.add(e);
        }
        // Newest first; the sort is stable, so equal stamps keep the provider's order.
        cards.sort((x, y) -> Long.compare(y.lastEngagement, x.lastEngagement));
        while (cards.size() > rowSize) rest.add(0, cards.remove(cards.size() - 1));
        tiles.clear();
        tiles.addAll(band);
        tiles.addAll(cards);
        tiles.addAll(rest);
        return cards.size();
    }

    /**
     * Moves the Continue watching tiles to the row at {@code start}, newest first, the same zone rule as
     * {@link #placeContentRow}: the dock band keeps exactly the tiles it had.
     */
    private static void placeFollowRow(List<Entry> tiles, int start, Map<String, Long> opened, int rowSize) {
        if (start >= tiles.size() || opened.isEmpty() || rowSize <= 0) return;
        List<Entry> zone = new ArrayList<>(tiles.subList(0, start));
        List<Entry> row = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = start; i < tiles.size(); i++) {
            Entry e = tiles.get(i);
            // A content card sharing its package with a docked app's tile is not that app.
            if (e.launch != null || row.size() == rowSize || !opened.containsKey(e.pkg)) rest.add(e);
            else row.add(e);
        }
        row.sort((x, y) -> Long.compare(opened.get(y.pkg), opened.get(x.pkg)));
        tiles.clear();
        tiles.addAll(zone);
        tiles.addAll(row);
        tiles.addAll(rest);
    }

    /**
     * Re-applies the follow row after a return to Home, so apps opened while Home was away lead the
     * first grid row again. Nothing is re-decoded: the same drawables keep their entries. Returns
     * whether anything moved.
     */
    boolean applyFollowRow() {
        if (!followRow || items.isEmpty() || !refreshFollowRow(items)) return false;
        int start = followRowStart();
        if (singleRow) {
            int content = placeContentRow(items, start, dockSize);
            placeFollowRow(items, start + content, openedByPkg, dockSize - content);
        } else {
            placeFollowRow(items, start, openedByPkg, dockSize);
        }
        notifyDataSetChanged();
        return true;
    }

    /** icon_tiles: the app's icon centred on a dark banner-shaped tile, for apps whose own banner crops badly. */
    private Drawable iconBanner(Drawable icon) {
        int w = 320, h = 180, side = 104;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        canvas.drawColor(0xFF15202B);
        icon.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
        icon.draw(canvas);
        return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bmp);
    }

    /** A banner_* drawable bundled with the launcher, or null for none or a name that is not one. */
    private Drawable bundledBanner(String name) {
        if (name == null || !name.startsWith("banner_")) return null;
        int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        return id != 0 ? ctx.getDrawable(id) : null;
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        Drawable banner = null;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom;
                // banner_density caps that for lean-memory themes, banner_565 halves the pixels (config.xml).
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                if (bannerMaxDensity > 0) density = Math.min(density, bannerMaxDensity);
                banner = decodeBanner(resources, id, density);
                // Both keys off (or an undecodable/non-bitmap resource) keeps the #131 route:
                // the explicit xhdpi-or-panel density request, not the PackageManager fallback.
                if (banner == null) banner = resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        if (banner == null) banner = ri.activityInfo.loadBanner(pm);
        // A square "banner" counts as none: the tile shows the app's icon and name instead of a blur.
        return banner != null && isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight()) ? banner : null;
    }

    /**
     * The banner's bitmap decoded with the theme's memory budget (config.xml banner_565 and banner_density):
     * RGB_565 when the theme asks, scaled to the theme's density cap, and never upscaled from the file.
     * null keeps the upstream path (getDrawableForDensity at xhdpi-or-panel density, ARGB_8888).
     */
    private Drawable decodeBanner(Resources resources, int id, int density) {
        if (!banner565 && bannerMaxDensity <= 0) return null;
        try {
            TypedValue value = new TypedValue();
            InputStream in = resources.openRawResource(id, value);
            Bitmap bitmap;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = banner565 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
                options.inTargetDensity = density;
                bitmap = BitmapFactory.decodeResourceStream(resources, value, in, null, options);
            } finally {
                in.close();
            }
            return bitmap != null ? new BitmapDrawable(resources, bitmap) : null;
        } catch (IOException | Resources.NotFoundException ignored) {
            return null;
        }
    }

    /**
     * The Continue watching content cards: the TV provider's watch_next_program rows, newest first. Empty when
     * the provider cannot be read (no READ_TV_LISTINGS) or publishes nothing, so the row is app recents only.
     */
    /** Diagnostics for the continue-watching row: row count, cards built, and every poster failure. */
    static final String WATCH_TAG = "WatchRowDebug";

    private List<Entry> contentEntries() {
        List<Entry> entries = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<WatchNext.Item> rows = WatchNext.load(ctx, System.currentTimeMillis());
        Log.i(WATCH_TAG, "reload: provider rows=" + rows.size());
        // Only the row's worth (WatchNext.MAX_ITEMS): surplus published rows must not fill the grid up.
        for (WatchNext.Item item : rows) {
            if (entries.size() == WatchNext.MAX_ITEMS) break;
            // A publisher row without a name is unusable: no label, no card.
            if (item.title == null || item.title.isEmpty()) {
                Log.i(WATCH_TAG, "card dropped: untitled row");
                continue;
            }
            Intent deepLink = deepLink(item.intentUri);
            if (deepLink == null) {
                Log.i(WATCH_TAG, "card dropped: unparsable link " + item.intentUri);
                continue;
            }
            // The resolver and a random browser are never the answer: the card binds the row's publisher
            // (falls back to that app's launcher, drops the card when neither opens; see WatchNext.openable).
            Intent link = deepLink;
            deepLink = WatchNext.openable(link,
                    link.getPackage(), item.packageName,
                    pkg -> {
                        Intent bound = new Intent(link).setPackage(pkg);
                        return pm.resolveActivity(bound, 0) == null ? null : bound;
                    },
                    pkg -> {
                        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                        return launch == null ? null : launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    });
            if (deepLink == null) continue;
            String pkg = targetPackage(deepLink);
            // Same separator as Entry.name (the caption line under a focused tile).
            String label = item.episodeTitle == null || item.episodeTitle.isEmpty()
                    ? item.title : item.title + " \u00B7 " + item.episodeTitle;
            // The deep link is the card's identity: several rows carry one app's package.
            ComponentName component = new ComponentName(pkg, item.intentUri);
            // The card says whose content it is in its two-line caption under the tile (image →
            // title → app badge, like the platform's ImageCardView) — never a chip over the poster.
            Drawable appIcon = null;
            try {
                appIcon = pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {
            }
            Entry entry = new Entry(Entry.APP, label, component, poster(item.posterUri), appIcon, null, 0,
                    deepLink, item.lastEngagement, item.posterUri);
            entry.progressPct = WatchNext.progress(item.positionMillis, item.durationMillis);
            if (entry.banner != null && entry.progressPct > 0) {
                entry.banner = withProgress(entry.banner, entry.progressPct);
            }
            entry.appName = appLabel(pkg);
            entry.detail = detailCaption(label, appIcon, entry.appName);
            entries.add(entry);
        }
        return entries;
    }

    /** The intent a content card opens, already carrying NEW_TASK so Home can start it; null if the URI gives none. */
    private static Intent deepLink(String uri) {
        try {
            Intent intent = uri.startsWith("intent:")
                    ? Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    /** The app a content card names in its focus line: the installed app's label; null when unknown. */
    private String appLabel(String pkg) {
        if (pkg == null) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label == null ? null : label.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** A content card's caption: line 1 the title, line 2 the app (its small icon, then its name). */
    private CharSequence detailCaption(String label, Drawable icon, String appName) {
        SpannableStringBuilder detail = new SpannableStringBuilder(label);
        if (appName == null || appName.isEmpty() || icon == null) return detail;
        float density = ctx.getResources().getDisplayMetrics().density;
        int size = Math.round(18f * density);
        Drawable badge = null;
        try {
            // A rasterized bitmap keeps the span at its fixed size: a raw (often adaptive) app icon
            // does not shrink to the text line and buries the caption under a huge drawing.
            Bitmap badgeBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(badgeBitmap);
            icon.setBounds(0, 0, size, size);
            icon.draw(canvas);
            badge = new BitmapDrawable(ctx.getResources(), badgeBitmap);
            // A fresh drawable has empty bounds: the image span draws exactly them, so set its own.
            badge.setBounds(0, 0, size, size);
        } catch (RuntimeException ignored) {
            return detail;
        }
        detail.append('\n');   // line 2 starts under the title
        int slot = detail.length();
        detail.append('\uFFFC');
        detail.setSpan(new ImageSpan(badge, ImageSpan.ALIGN_BASELINE), slot, slot + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        detail.append(' ').append(appName);
        return detail;
    }

    /** The poster wearing its playback progress bar at the foot: only when the row publishes one. */
    private Drawable withProgress(Drawable poster, int pct) {
        int w = poster.getIntrinsicWidth(), h = poster.getIntrinsicHeight();
        if (w <= 0 || h <= 0 || pct <= 0 || pct > 100) return poster;
        float density = ctx.getResources().getDisplayMetrics().density;
        int bar = Math.round(8f * density), margin = Math.round(14f * density);
        float radius = 4f * density;
        float[] corners = {radius, radius, radius, radius, radius, radius, radius, radius};
        ShapeDrawable track = new ShapeDrawable(new RoundRectShape(corners, null, null));
        track.getPaint().setColor(0x59000000);
        int fillW = Math.round((w - 2 * margin) * pct / 100f);
        ShapeDrawable fill = new ShapeDrawable(new RoundRectShape(corners, null, null));
        fill.getPaint().setColor(0xF2FFFFFF);
        LayerDrawable layered = new LayerDrawable(new Drawable[]{poster, track, fill});
        layered.setLayerInset(1, margin, h - bar - margin, margin, margin);
        // LayerDrawable draws each layer over the poster's whole bounds minus the insets: the fill's
        // right inset carves it down to the published percent, and never stretches it.
        layered.setLayerInset(2, margin, h - bar - margin, Math.max(0, w - margin - fillW), margin);
        return layered;
    }

    /** The app a content card belongs to: the deep link's package, else the app that handles it, else none. */
    private String targetPackage(Intent launch) {
        if (launch.getPackage() != null) return launch.getPackage();
        if (launch.getComponent() != null) return launch.getComponent().getPackageName();
        ResolveInfo resolved = ctx.getPackageManager().resolveActivity(launch, 0);
        if (resolved != null && resolved.activityInfo != null) return resolved.activityInfo.packageName;
        return WATCH_NEXT_PKG;
    }

    /**
     * A content card's poster, decoded no wider than {@link #POSTER_MAX_W} in RGB_565 (the same memory care as
     * {@link #decodeBanner}). null on any failure: the tile then shows the fallback of a bannerless tile.
     */
    /**
     * Poster art lives in a disk cache keyed by the art URL: the apps publish signed CDN URLs that
     * expire within hours, so the copy we already fetched keeps the card dressed after the URL dies.
     * I/O only ever runs on the poster pool (the catch path skips it on the main thread).
     */
    private File posterFile(String uri) {
        File dir = new File(ctx.getCacheDir(), "posters");
        return new File(dir, Integer.toHexString(uri.hashCode()) + ".jpg");
    }

    private Drawable poster(String uri) {
        if (uri == null || uri.isEmpty()) return null;
        Drawable cached = POSTERS.get(uri);
        if (cached != null) return cached;
        try {
            Uri source = Uri.parse(uri);
            BitmapFactory.Options size = new BitmapFactory.Options();
            size.inJustDecodeBounds = true;
            try (InputStream in = posterStream(source)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, size);
            }
            int sample = 1;
            while (size.outWidth > 0 && size.outWidth / sample > POSTER_MAX_W) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = posterStream(source)) {
                if (in == null) return null;
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            Drawable drawable = bitmap != null ? new BitmapDrawable(ctx.getResources(), bitmap) : null;
            if (drawable != null) {
                POSTERS.put(uri, drawable);
                try {
                    File file = posterFile(uri);
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                    }
                } catch (IOException disk) {
                    Log.i(WATCH_TAG, "poster disk-save FAILED: " + disk);
                }
                Log.i(WATCH_TAG, "poster ok: " + uri);
            } else {
                Log.i(WATCH_TAG, "poster empty (server answered, no image): " + uri);
            }
            return drawable;
        } catch (IOException | RuntimeException e) {
            // The published URL expired (signed CDN links live for hours, the row can outlive it):
            // fall back to the copy we already fetched, when it is legal to touch the disk at all.
            Log.i(WATCH_TAG, "poster FAILED (" + e.getClass().getSimpleName() + "): " + uri);
            if (Looper.myLooper() == Looper.getMainLooper()) return null;
            try {
                File file = posterFile(uri);
                if (!file.isFile()) return null;
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) return null;
                Drawable drawable = new BitmapDrawable(ctx.getResources(), bitmap);
                POSTERS.put(uri, drawable);
                Log.i(WATCH_TAG, "poster recovered from disk cache: " + uri);
                return drawable;
            } catch (RuntimeException disk) {
                Log.i(WATCH_TAG, "poster disk-cache read FAILED: " + disk);
                return null;
            }
        }
    }

    /** The poster's bytes: the provider's own streams, plus the http(s) art the apps publish as URLs. */
    private InputStream posterStream(Uri source) throws IOException {
        String scheme = source.getScheme() == null ? "" : source.getScheme();
        if (scheme.startsWith("http")) {
            HttpURLConnection connection = (HttpURLConnection) new URL(source.toString()).openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(8000);
            return connection.getInputStream();
        }
        return ctx.getContentResolver().openInputStream(source);
    }

    /**
     * Fetches the watch-next cards' posters off the main thread (https art cannot decode on it) and
     * refreshes the row when they land. Cached art never reaches the pool, so a rebuild is instant.
     */
    private void fetchMissingPosters(List<Entry> cards) {
        List<Entry> missing = new ArrayList<>();
        for (Entry e : cards) {
            if (e.banner == null && e.posterUri != null && !e.posterUri.isEmpty()
                    && !POSTERS.containsKey(e.posterUri)) missing.add(e);
        }
        if (missing.isEmpty()) return;
        posterPool.execute(() -> {
            boolean arrived = false;
            for (Entry e : missing) {
                Drawable art = poster(e.posterUri);
                if (art != null) {
                    e.banner = e.progressPct > 0 ? withProgress(art, e.progressPct) : art;
                    arrived = true;
                }
            }
            if (arrived) mainHandler.post(() -> {
                Log.i(WATCH_TAG, "posters arrived: notify rowAdapter=" + (rowAdapter != null));
                notifyDataSetChanged();
                // The sideways row's own adapter keys on the card list, and the list did not change:
                // poster arrivals rebind it directly, or the row keeps its icon fallback forever.
                if (rowAdapter != null) rowAdapter.notifyDataSetChanged();
            });
        });
    }

    /**
     * Some apps declare a small square image as their banner (Rockchip's Media Center ships a 64 px one). Cropped
     * over a 16:9 tile it is a blur. A drawable with no intrinsic size (a colour, a shape) still fills the tile.
     */
    static boolean isBannerShaped(int width, int height) {
        return width <= 0 || height <= 0 || width >= height * 1.4f;
    }

    /** Lets go of every tile and its artwork (Home is in the background and memory is short). */
    void clear() {
        items.clear();
        contentCards.clear();
        watchRowPos = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LIBRARY) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.row_app_library, parent, false);
            View button = row.findViewById(R.id.btnLibrary);
            button.setOnClickListener(v -> listener.onLibraryRequested());
            button.setOnFocusChangeListener((v, focused) -> { if (focused) listener.onLibraryFocused(); });
            button.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_UP) listener.onLibraryUp();
                return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                        || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            return new RecyclerView.ViewHolder(row) { };
        }
        if (viewType == TYPE_WATCH_ROW) {
            // Continue watching: one full-width grid item holding a sideways row of the content cards.
            View row = LayoutInflater.from(ctx).inflate(R.layout.row_watch_next, parent, false);
            RecyclerView sideways = row.findViewById(R.id.watchRow);
            sideways.setLayoutManager(new LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false));
            sideways.setItemAnimator(null);
            sideways.setAdapter(rowAdapter = new RowAdapter());
            return new RecyclerView.ViewHolder(row) { };
        }
        return createTile(parent, false);
    }

    /**
     * One tile, laid out for the grid (row false) or for the sideways row. The row's slot is a card plus a gap
     * wide, and the card stays centred in it, so two cards of the row keep the spacing of two grid tiles.
     */
    private Holder createTile(ViewGroup parent, boolean row) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        // The row has no dock band or rows under it to make room for: its cards take the width-only size.
        int w = row ? tileWidthPx(inner, dockSize, density)
                : tileWidthPx(inner, parent.getHeight(), dockSize, rowsBelowDock, dockTitle, dockRow, density);
        int h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round((dockRow ? 7 : 4) * density);
        if (!dockRow) {
            // Compact spacing (COMPACT_ITEM_EXTRA_DP): 4dp item padding and a 20dp name instead of 8dp and 22dp.
            nameLp.height = Math.round(20 * density);
            int pad = Math.round(4 * density);
            v.setPadding(v.getPaddingLeft(), pad, v.getPaddingRight(), pad);
        }
        name.setLayoutParams(nameLp);
        if (row) {
            ViewGroup.LayoutParams itemLp = v.getLayoutParams();
            itemLp.width = w + Math.round(ROW_CARD_GAP_DP * density);
            v.setLayoutParams(itemLp);
        } else if (singleRow) {
            // A sideways row would stretch a match_parent item across the whole screen: give it a grid column's width.
            v.getLayoutParams().width = inner / dockSize;
        }
        if (uniformTiles) {
            // tvOS-style tile: a large centred mark, no caption inside the card.
            View icon = v.findViewById(R.id.icon);
            FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
            iconLp.width = iconLp.height = Math.round(h * ICON_SHARE);
            iconLp.bottomMargin = 0;
            icon.setLayoutParams(iconLp);
        } else if (h < Math.round(DESIGN_H_DP * density)) {
            // Shrunk tiles (e.g. 5 or 6 columns): scale icon and bottom margin proportionally with tile height so the label does not overlap the icon.
            View icon = v.findViewById(R.id.icon);
            FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
            float scale = (float) h / (DESIGN_H_DP * density);
            iconLp.width = iconLp.height = Math.round(48f * density * scale);
            iconLp.bottomMargin = Math.round(14f * density * scale);
            icon.setLayoutParams(iconLp);
            View label = v.findViewById(R.id.label);
            label.setPadding(label.getPaddingLeft(), label.getPaddingTop(), label.getPaddingRight(), Math.round(4f * density));
        }
        return new Holder(v, cardOutline, row ? rowTiles : listener, animations, advancedEffects, reducedMotion,
                restNameAlpha, focusOvershoot, row);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isLibraryAt(position)) return;
        if (isWatchRowAt(position)) {
            bindWatchRow(holder.itemView);
            return;
        }
        Holder h = (Holder) holder;
        int index = tileIndexAt(position);
        // The dock has no labels, unless always_show_names asks for them (DockDecoration then makes room in the band).
        // In a single row every tile is the dock's kind: the big title above names it.
        bindTile(h, items.get(index), position, dockRow && (singleRow || index < dockSize));
    }

    /**
     * Draws a tile's card, its caption and its deep link. {@code menuPosition} is the tile's adapter position
     * for the tile menu; the row's cards pass -1, since a content card cannot be moved, docked or removed.
     */
    private void bindTile(Holder h, Entry e, int menuPosition, boolean dockKind) {
        if (e.kind == Entry.EMPTY) {
            // A free dock slot keeps its place in the row and the band, but shows nothing and takes no focus.
            h.card.setVisibility(View.INVISIBLE);
            h.card.setFocusable(false);
            h.name.setVisibility(View.INVISIBLE);
            h.banner.setImageDrawable(null);
            h.icon.setImageDrawable(null);
            h.card.setOnClickListener(null);
            h.card.setOnLongClickListener(null);
            return;
        }
        h.card.setVisibility(View.VISIBLE);
        h.card.setFocusable(true);
        boolean card = e.kind == Entry.APP && e.launch != null;
        if (card) {
            // The card's caption is two lines (image → title → app badge, like the platform's
            // ImageCardView) and always visible: knowing whose content it is must not need focus.
            CharSequence caption = e.detail;
            if (caption != null) {
                // A long title must wrap into the badge line and hide the app: the title line is
                // ellipsized to the tile's own width, and the caption is rebuilt whole from the
                // stored app name (a subSequence join was losing the image span of the badge).
                int nl = TextUtils.indexOf(caption, "\n");
                if (nl > 0 && h.name.getLayoutParams().width > 0) {
                    CharSequence title = TextUtils.ellipsize(caption.subSequence(0, nl),
                            h.name.getPaint(), h.name.getLayoutParams().width,
                            TextUtils.TruncateAt.END);
                    if (title.length() != nl) {
                        caption = detailCaption(title.toString(), e.icon, e.appName);
                    }
                }
            }
            h.name.setText(caption != null ? caption : e.name);
            h.name.setSingleLine(false);
            // The XML's single-line transformation method renders '\n' as a space; drop it or the
            // two-line caption collapses into one running line.
            h.name.setTransformationMethod(null);
            h.name.setLines(2);
            int two = Math.round(40f * ctx.getResources().getDisplayMetrics().density);
            if (h.name.getLayoutParams().height != two) {
                h.name.getLayoutParams().height = two;
                h.name.requestLayout();
            }
            h.name.setAlpha(1f);
        } else {
            h.name.setText(e.name);
            if (h.name.getMaxLines() != 1) {
                h.name.setSingleLine(true);
                h.name.getLayoutParams().height = h.nameHeight;
                h.name.requestLayout();
            }
        }
        // The dock has no labels, unless always_show_names asks for them (DockDecoration then makes room in the band).
        // In a single row every tile is the dock's kind: the big title above names it.
        h.name.setVisibility(dockKind && restNameAlpha == 0f ? View.INVISIBLE : View.VISIBLE);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        if (!card) h.name.setAlpha(focused ? 1f : restNameAlpha);
        h.card.setScaleX(focused && animations ? focusScale() : 1f);
        h.card.setScaleY(focused && animations ? focusScale() : 1f);
        h.card.setTranslationY(focused && advancedEffects ? -4f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.card.setTranslationZ(focused && advancedEffects ? 8f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.banner.setScaleX(focused && advancedEffects ? 1.035f : 1f);
        h.banner.setScaleY(focused && advancedEffects ? 1.035f : 1f);
        // A fully opaque banner covers the whole card (centerCrop), so the card's own fill under it is drawn for
        // nothing on every frame; keep it under banners with transparent parts and under icon tiles.
        h.card.setBackground(e.banner != null && e.banner.getOpacity() == PixelFormat.OPAQUE ? null : h.cardBackground);
        if (uniformTiles && e.banner == null) {
            if (e.backdrop == null) e.backdrop = backdrop(e.icon, ambientOf(e));
            h.card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, e.backdrop));
        }
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            // A content card with no poster stays a clean tile: its app already lives in the badge
            // under the tile, and a big centred icon would read the card as an app tile.
            h.icon.setImageDrawable(card ? null : e.icon);
            h.icon.setVisibility(card ? View.GONE : View.VISIBLE);
            h.label.setText(e.label);
            // A content card says whose content it is in its two-line caption below the tile: the
            // tile's own label would read the same title twice while the poster is on its way.
            h.label.setVisibility(uniformTiles || card ? View.GONE : View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            // A content card is no tile: with the tile menu on it keeps the app-info answer of every other case.
            if (tileMenu && menuPosition >= 0) {
                listener.onTileLongPress(menuPosition, v);
                return true;
            }
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /**
     * Puts focus on the sideways row's card {@code cardIndex}, given the grid's holder for the row item; false
     * when the row has not been laid out yet (the caller focuses it once it has). A card beyond what is laid
     * out falls back to the last laid-out card, so a deep column still lands on the row.
     */
    boolean focusRowCard(RecyclerView.ViewHolder rowHolder, int cardIndex) {
        if (rowHolder == null) return false;
        RecyclerView sideways = rowHolder.itemView.findViewById(R.id.watchRow);
        if (sideways == null || sideways.getChildCount() == 0) return false;
        RecyclerView.ViewHolder target = sideways.findViewHolderForAdapterPosition(cardIndex);
        View card = target != null ? target.itemView
                : sideways.getChildAt(Math.min(cardIndex, sideways.getChildCount() - 1));
        return card != null && card.requestFocus();
    }

    /**
     * How far a focused card's zoom reaches past the row item's bounds, in px: the card scales around its
     * center ({@link #bindTile} uses 1.10, 1.12 with advanced effects, none without animations), so its
     * bottom half sticks out by half the growth. The row's scroll margin needs this on top of its bounds.
     */
    int focusOvershootPx(int itemHeightPx) {
        if (!animations) return 0;
        float scale = advancedEffects ? 1.12f : 1.10f;
        return (int) Math.ceil(itemHeightPx * (scale - 1f) / 2f);
    }

    /** Puts the current content cards in the sideways row and keeps it on its first card. */
    private void bindWatchRow(View row) {
        RecyclerView sideways = row.findViewById(R.id.watchRow);
        RowAdapter adapter = (RowAdapter) sideways.getAdapter();
        if (adapter == null) sideways.setAdapter(adapter = new RowAdapter());
        // The live adapter is whichever instance this row's holders listen to: the poster-arrival notify
        // reaches it only through this field, so every path that binds a row keeps it current (a recycled
        // holder brings its own adapter the create path never saw).
        rowAdapter = adapter;
        if (adapter.submit(contentCards)) sideways.scrollToPosition(0);
    }

    /** The sideways row's live adapter: poster arrivals must rebind it (an outer notify rebinds the header). */
    private RowAdapter rowAdapter;

    /**
     * The sideways row's own adapter: the same cards the grid drew, as many as the provider published (up to
     * {@link WatchNext#MAX_ITEMS}). Its positions are card indices, which is what {@link #rowTiles} reads.
     */
    private final class RowAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Entry> shown = new ArrayList<>();
        private List<String> keys = Collections.emptyList();

        /** Takes the new cards; true when the row changed (it then goes back to its first card). */
        boolean submit(List<Entry> next) {
            List<String> nextKeys = new ArrayList<>();
            for (Entry e : next) nextKeys.add(e.component.flattenToShortString());
            if (nextKeys.equals(keys)) return false;
            keys = nextKeys;
            shown.clear();
            shown.addAll(next);
            notifyDataSetChanged();
            return true;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return createTile(parent, true);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Entry e = shown.get(position);
            Log.i(WATCH_TAG, "row bind " + position + " banner=" + (e.banner != null)
                    + " uri=" + (e.posterUri == null ? "null" : "set"));
            bindTile(holder, e, -1, false);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    /** Opens the tile: a content card its published deep link, every other tile its activity by component. */
    private void launch(Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (e.launch != null) {
            try {
                ctx.startActivity(e.launch);
                // A card with no installed app behind it has no history to record.
                if (!WATCH_NEXT_PKG.equals(e.pkg)) LastOpened.record(ctx, e.pkg);
                if (advancedEffects && !reducedMotion && ctx instanceof Activity) {
                    ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                }
            } catch (Exception ex) {
                Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        // TV apps launch with the same leanback intent as from the stock launcher or the remote, so an app
        // already running resumes instead of restarting; only phone apps added from the library use plain MAIN.
        if (leanbackPackages.contains(e.pkg)) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        String action = launchActions.get(e.pkg);
        if (action != null) {
            // NEW_TASK only: with RESET_TASK_IF_NEEDED, Android just brings a running task of the app to the front
            // ("Activity not started"), so the screen never opens while the app is up, e.g. MagentaTV's boot-time task.
            Intent byAction = new Intent(action).setPackage(e.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (ctx.getPackageManager().resolveActivity(byAction, 0) != null) i = byAction;
        }
        try {
            ctx.startActivity(i);
            // The cinematic shelf and the Continue watching row both read it, on every open.
            LastOpened.record(ctx, e.pkg);
            if (advancedEffects && !reducedMotion && ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
            }
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return tileRows() + (showLibrary ? 1 : 0);
    }

    /** Adapter positions of the tiles plus the Continue watching row, before the App library row. */
    private int tileRows() { return items.size() + (watchRowPos >= 0 ? 1 : 0); }

    int appCount() { return items.size(); }

    /** Adapter position of the Continue watching row, or -1 when it is not shown. */
    int watchRowPosition() { return watchRowPos; }

    boolean isWatchRowAt(int position) { return watchRowPos >= 0 && position == watchRowPos; }

    /** The row takes the whole width: in the grid it is one item across every column, like the App library. */
    boolean isFullSpan(int position) { return isWatchRowAt(position) || isLibraryAt(position); }

    int libraryPosition() { return showLibrary ? tileRows() : -1; }

    boolean isLibraryAt(int position) { return showLibrary && position == tileRows(); }

    /** True when the App library pill follows the last tile (it is then where Left/Right and Down leave to). */
    boolean libraryFollows() { return showLibrary; }

    /** The tile at an adapter position: every tile at or after the row sits one place further. */
    int tileIndexAt(int position) { return WatchRow.tileIndex(position, watchRowPos); }

    int adapterPositionOf(int tileIndex) { return WatchRow.adapterPosition(tileIndex, watchRowPos); }

    /** True when the Continue watching row's own line is directly above/below the tile at this adapter position. */
    boolean watchRowAbove(int position) {
        return WatchRow.rowAbove(tileIndexAt(position), watchRowPos, dockSize);
    }

    boolean watchRowBelow(int position) {
        return WatchRow.rowBelow(tileIndexAt(position), watchRowPos, dockSize);
    }

    @Override
    public int getItemViewType(int position) {
        if (isWatchRowAt(position)) return TYPE_WATCH_ROW;
        if (isLibraryAt(position)) return TYPE_LIBRARY;
        return TYPE_TILE;
    }

    boolean containsPackage(String pkg) {
        for (Entry entry : items) if (entry.pkg.equals(pkg)) return true;
        return false;
    }

    @Override
    public long getItemId(int position) {
        if (isLibraryAt(position)) return Long.MIN_VALUE;
        if (isWatchRowAt(position)) return Long.MIN_VALUE + 1;
        return items.get(tileIndexAt(position)).component.flattenToShortString().hashCode();
    }

    /** Uses this saved order and dock size from the next reload() (Order.ranks, Order.docked). */
    void setSavedOrder(Map<String, Integer> ranks, int docked) {
        savedRanks = ranks;
        savedDocked = docked;
    }

    /**
     * A tile in the dock: one of the first savedDocked tiles of the saved order when one was recorded, so an app
     * the owner moved into the dock stays there (#499); else a dock_apps entry.
     */
    private boolean isDocked(Entry e) {
        // A content card never holds a dock slot, whatever package its deep link resolved to.
        if (e.launch != null) return false;
        if (savedDocked < 0) return favourites.contains(e.pkg);
        Integer rank = savedRanks.get(e.component.flattenToShortString());
        return rank != null && rank < savedDocked;
    }

    /** Reorder mode on or off; takes effect at the next reload() (empty dock slots are left out while on). */
    void setReordering(boolean on) {
        reordering = on;
    }

    boolean hasEmptySlots() {
        return emptySlots > 0;
    }

    /** Tiles that can be moved: everything but the "Default home" tile, which always stays last. */
    int movableCount() {
        int n = items.size();
        return n > 0 && items.get(n - 1).kind == Entry.HOME_SETTINGS ? n - 1 : n;
    }

    /** Keys of the movable tiles in their current order, for Order (empty dock slots are not tiles). */
    List<String> movableKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < movableCount(); i++) {
            Entry e = items.get(i);
            if (e.kind != Entry.EMPTY) keys.add(e.component.flattenToShortString());
        }
        return keys;
    }

    /** Moves one tile; the others shift by one. Stable ids keep the moved tile's view (and its focus). */
    void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= movableCount() || to >= movableCount()) return;
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
        // The dock row hides names by position: rebind the tiles whose side of the dock edge changed.
        if (from < dockSize || to < dockSize) {
            int lo = Math.min(from, to), hi = Math.max(from, to);
            notifyItemRangeChanged(lo, hi - lo + 1);
        }
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    /** Dock slots that hold an app: the whole dock unless some slots are empty. */
    int dockFilled() {
        return emptySlots > 0 ? docked : dockSize;
    }

    /** A position focus can land on: an empty dock slot gives the last dock app. */
    int focusablePosition(int position) {
        return DockSlots.focusable(position, docked, emptySlots);
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    String packageAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : null;
    }

    /** The tile's artwork, its banner or else its icon, for the cinematic shelf's backdrop (HeroArt). */
    Drawable artworkAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        Entry e = items.get(position);
        return e.banner != null ? e.banner : e.icon;
    }

    float focusScale() {
        return !animations ? 1f : advancedEffects ? 1.12f : 1.10f;
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        return ambientOf(items.get(position));
    }

    private int ambientOf(Entry e) {
        if (e.ambient == 0) {
            e.ambient = uniformTiles && e.banner == null ? dominantColor(e.icon) : vividColor(e.banner != null ? e.banner : e.icon);
        }
        return e.ambient;
    }

    /** Share of the tile height the icon takes in uniform_tiles mode (onCreateViewHolder). */
    private static final float ICON_SHARE = 0.62f;

    /**
     * The colour covering most of an icon, for a card that should read as that app: a small saturated badge
     * (a red "new" corner on a blue icon) must not win, as it can with {@link #vividColor}. Grey if none.
     */
    private static int dominantColor(Drawable live) {
        if (live == null) return 0xFF6E6E73;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 24;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bmp));
        d.setBounds(saved);
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        float[] hsv = new float[3];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;   // white, grey and black carry no hue
                int bin = (int) (hsv[0] / 30f) % 12;
                float w = hsv[1] * hsv[2];
                weight[bin] += w;
                sum[bin][0] += Color.red(p) * w;
                sum[bin][1] += Color.green(p) * w;
                sum[bin][2] += Color.blue(p) * w;
            }
        }
        bmp.recycle();
        int best = 0;
        for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] < n * n * 0.03f) return 0xFF6E6E73;   // a monochrome icon, like a grey gear
        float w = weight[best];
        return Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
    }

    /**
     * The uniform tile's fill. Where the icon sits on a flat colour of its own (a white or coloured rounded
     * square), the card takes that colour, so the mark reads as filling the tile as on tvOS. Otherwise a
     * gradient of the icon's main colour, deepened so the mark stands out. The card clips the corners.
     */
    private static int[] backdrop(Drawable live, int fallback) {
        int[] plate = iconPlate(live);
        if (plate != null) return plate;
        float[] hsv = new float[3];
        Color.colorToHSV(fallback, hsv);
        // Yellow darkened turns olive: lean it to amber instead.
        if (hsv[0] > 42f && hsv[0] < 70f && hsv[1] > 0.3f) hsv[0] = 36f;
        hsv[2] = Math.min(hsv[2], 0.62f);
        int top = Color.HSVToColor(hsv);
        hsv[2] *= 0.6f;
        return new int[]{top, Color.HSVToColor(hsv)};
    }

    /**
     * The plate an icon's artwork sits on (a flat or vertically graded rounded square), carried out to the
     * card's top and bottom edges so the mark reads as filling the tile; null when the icon has no
     * opaque plate (a bare mark on transparency).
     */
    private static int[] iconPlate(Drawable live) {
        if (live == null) return null;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 48;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bmp));
        d.setBounds(saved);
        // Just inside a rounded square's corners; one side may go a step further in, past a corner badge.
        // An icon padded on both sides is a small plate in the middle, which no fill would meet.
        int a = plateColor(bmp, 7, n), b = plateColor(bmp, n - 8, n);
        int topRow = 7, bottomRow = n - 8;
        if (a == 0 && b != 0) a = plateColor(bmp, topRow = 10, n);
        else if (b == 0 && a != 0) b = plateColor(bmp, bottomRow = n - 11, n);
        bmp.recycle();
        if (a == 0 || b == 0) return null;
        // The sampled rows sit at these heights of the tile; extend the line through them to its edges. A row
        // taken further in sits on artwork-side plate, so its slope is less certain: cap how far it reaches.
        float margin = (1f - ICON_SHARE) / 2f;
        float yA = margin + ICON_SHARE * topRow / n, yB = margin + ICON_SHARE * bottomRow / n;
        float t0 = -yA / (yB - yA), t1 = (1f - yA) / (yB - yA);
        if (topRow != 7 || bottomRow != n - 8) {
            t0 = Math.max(-0.35f, t0);
            t1 = Math.min(1.35f, t1);
        }
        return new int[]{mix(a, b, t0), mix(a, b, t1)};
    }

    /** {@link #plateColor(int[])} of row y, between the same insets as the row's distance from the edge. */
    private static int plateColor(Bitmap bmp, int y, int n) {
        int inset = Math.min(y, n - 1 - y);
        int[] row = new int[n - 2 * inset];
        bmp.getPixels(row, 0, row.length, inset, y, row.length, 1);
        return plateColor(row);
    }

    /** The colour most of a row shares, ignoring artwork that crosses it; 0 if the row is not mostly plate. */
    private static int plateColor(int[] row) {
        int[] r = new int[row.length], g = new int[row.length], b = new int[row.length];
        int opaque = 0;
        for (int p : row) {
            if (Color.alpha(p) < 240) continue;
            r[opaque] = Color.red(p);
            g[opaque] = Color.green(p);
            b[opaque] = Color.blue(p);
            opaque++;
        }
        if (opaque < row.length * 0.9f) return 0;
        int mr = median(r, opaque), mg = median(g, opaque), mb = median(b, opaque);
        long sr = 0, sg = 0, sb = 0;
        int close = 0;
        for (int i = 0; i < opaque; i++) {
            if (Math.abs(r[i] - mr) + Math.abs(g[i] - mg) + Math.abs(b[i] - mb) >= 48) continue;
            sr += r[i];
            sg += g[i];
            sb += b[i];
            close++;
        }
        if (close < row.length * 0.7f) return 0;
        return Color.rgb((int) (sr / close), (int) (sg / close), (int) (sb / close));
    }

    private static int median(int[] values, int count) {
        int[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }

    /** a + (b - a) * t per channel, clamped; t outside 0..1 extrapolates. */
    private static int mix(int a, int b, float t) {
        return Color.rgb(clamp(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                clamp(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                clamp(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        int[] px = new int[n * n];
        bmp.getPixels(px, 0, n, 0, 0, n, n);
        bmp.recycle();
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int p : px) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            r += pr;
            g += pg;
            b += pb;
            int max = Math.max(pr, Math.max(pg, pb));
            int min = Math.min(pr, Math.min(pg, pb));
            float sat = max == 0 ? 0f : (max - min) / (float) max;
            float val = max / 255f;
            float score = sat * Math.min(1f, val * 1.4f);   // saturated and not too dark
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        /** item_app.xml's tile_bg: the dark card behind an icon tile, or behind a banner that has transparent parts. */
        final Drawable cardBackground;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        /** The name height onCreateViewHolder laid out (per density and dock row): tiles restore it. */
        final int nameHeight;
        final boolean animations;
        final boolean advancedEffects;
        final boolean reducedMotion;
        /** Alpha of the name while the tile is not focused (always_show_names). */
        final float restNameAlpha;
        final boolean overshoot;
        /** A sideways row's card: its caption never fades, it only makes room for the zoomed poster. */
        final boolean rowCaption;

        Holder(View v, ViewOutlineProvider cardOutline, TileListener listener, boolean animations, boolean advancedEffects,
               boolean reducedMotion, float restNameAlpha, boolean overshoot, boolean rowCaption) {
            super(v);
            card = v.findViewById(R.id.card);
            cardBackground = card.getBackground();
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            nameHeight = name.getLayoutParams().height;
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;
            this.restNameAlpha = restNameAlpha;
            this.overshoot = overshoot;
            this.rowCaption = rowCaption;

            card.setOutlineProvider(cardOutline);
            card.setClipToOutline(true);

            final float density = v.getResources().getDisplayMetrics().density;

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                banner.animate().cancel();
                name.animate().cancel();
                float scale = !animations ? 1f : advancedEffects ? 1.12f : 1.10f;
                float s = focused ? scale : 1f;
                if (!animations) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationY(0f);
                    view.setTranslationZ(0f);
                    banner.setScaleX(1f);
                    banner.setScaleY(1f);
                    name.setAlpha(focused || rowCaption ? 1f : restNameAlpha);
                    name.setTranslationY(0f);
                } else if (reducedMotion) {
                    // A short zoom with no hardware layer: building a layer for so short an animation costs more
                    // frames than it saves. The name just appears instead of fading.
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(focused ? Motion.REDUCED_MS : 60)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    name.setAlpha(focused || rowCaption ? 1f : restNameAlpha);
                    // Same place as the full motion's slide ends: the layout's -6dp is only its starting point, and
                    // left there the name overlaps the zoomed tile.
                    // Only on focus: snapping it back to -6dp while it fades out would jump it up over the tile.
                    // A row's caption never fades: unfocused it rests at its natural position, focused it slides
                    // down the same 4dp to clear the zoomed poster.
                    if (focused) name.setTranslationY(density * 4f);
                    else if (rowCaption) name.setTranslationY(0f);
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(advancedEffects ? (focused ? 220 : 170) : (focused ? 260 : 180))
                            .setInterpolator(focused && !overshoot ? new DecelerateInterpolator(1.5f)
                                    : new OvershootInterpolator(focused ? (advancedEffects ? 0.7f : 1.6f) : 0f))
                            .withLayer().start();
                    if (advancedEffects) {
                        banner.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                                .setDuration(280).start();
                    }
                    name.animate().alpha(focused || rowCaption ? 1f : restNameAlpha)
                            .translationY(focused ? density * 4f : rowCaption ? 0f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}

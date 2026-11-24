package dev.rikunode.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.WeakHashMap;

/** Collect only actual PR entries received by the original feed. No independent network API. */
public final class HoshioriPrTab {
    private static final String KEY = "showPrTab";
    private static final int ORIGINAL_COUNT = 11;
    private static final int MAX_ENTRIES = 300;
    private static final WeakHashMap<Object, Boolean> ADAPTERS = new WeakHashMap<>();
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Context app;
    private static WeakReference<ArrayAdapter<Entry>> visibleAdapter = new WeakReference<>(null);

    private HoshioriPrTab() {}

    public static boolean enabled(Context context) {
        return context != null && HoshioriSettingsActivity.enabled(context, KEY, false);
    }

    public static void initAdapter(Object adapter, Context context) {
        app = context.getApplicationContext();
        boolean show = enabled(context);
        ADAPTERS.put(adapter, show);
        if (!show) synchronized (ENTRIES) { ENTRIES.clear(); }
    }

    public static int count(Object adapter, int original) {
        return Boolean.TRUE.equals(ADAPTERS.get(adapter)) ? original + 1 : original;
    }

    public static boolean isPrPage(Object adapter, int position) {
        return position == ORIGINAL_COUNT && Boolean.TRUE.equals(ADAPTERS.get(adapter));
    }

    /** The stock onResume already rebuilds the TopFragment if its tab settings changed. */
    public static boolean configChanged(Object fragment) {
        try {
            Object adapter = fragment.getClass().getMethod("getAdapter").invoke(fragment);
            return Boolean.TRUE.equals(ADAPTERS.get(adapter)) != enabled(app);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.e("HoshioriPR", "Could not compare tab configuration", failure);
            return false;
        }
    }

    /** Called before the stock mute / PR filter, regardless of the mute list. */
    public static boolean capture(Object entry) {
        if (!enabled(app) || entry == null) return false;
        try {
            Class<?> type = entry.getClass();
            if (!Boolean.TRUE.equals(type.getMethod("isPrEntry").invoke(entry))) return false;
            Uri url = (Uri) type.getMethod("getUrlToBrowse").invoke(entry);
            Uri original = (Uri) type.getMethod("getOriginalUri").invoke(entry);
            String title = (String) type.getMethod("getTitle").invoke(entry);
            if (url != null && title != null &&
                    ("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))) {
                String key = original != null ? original.toString() : url.toString();
                boolean added;
                synchronized (ENTRIES) {
                    added = !ENTRIES.containsKey(key);
                    if (added) {
                        ENTRIES.put(key, new Entry(title, url, original));
                        if (ENTRIES.size() > MAX_ENTRIES)
                            ENTRIES.remove(ENTRIES.keySet().iterator().next());
                    }
                }
                if (added) {
                    Log.i("HoshioriPR", "Captured PR entry: " + title);
                    MAIN.post(HoshioriPrTab::refresh);
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.e("HoshioriPR", "Could not capture PR entry", failure);
            return false;
        }
    }

    private static List<Entry> snapshot() {
        synchronized (ENTRIES) { return new ArrayList<>(ENTRIES.values()); }
    }

    private static void refresh() {
        ArrayAdapter<Entry> adapter = visibleAdapter.get();
        if (adapter == null) return;
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(snapshot());
        adapter.notifyDataSetChanged();
    }

    public static View createView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView note = new TextView(context);
        note.setText("読み込んだ記事一覧から集めたPR記事");
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        note.setPadding(padding, padding, padding, padding);
        root.addView(note);
        ListView list = new ListView(context);
        ArrayAdapter<Entry> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, snapshot());
        list.setAdapter(adapter);
        visibleAdapter = new WeakReference<>(adapter);
        TextView empty = new TextView(context);
        empty.setText("まだPR記事が取得されていません。ほかのタブの記事一覧を読み込んでください。");
        empty.setPadding(padding, padding, padding, padding);
        root.addView(empty);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = adapter.getItem(position);
            if (entry == null) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(context, "com.hatena.android.bookmark.presentation.activity.WebViewActivity");
            intent.setData(entry.url);
            if (entry.original != null) intent.putExtra("original_uri", entry.original);
            context.startActivity(intent);
        });
        return root;
    }

    private static final class Entry {
        final String title;
        final Uri url, original;
        Entry(String title, Uri url, Uri original) {
            this.title = title; this.url = url; this.original = original;
        }
        @Override public String toString() {
            return title + "\n" + url.getHost();
        }
    }
}

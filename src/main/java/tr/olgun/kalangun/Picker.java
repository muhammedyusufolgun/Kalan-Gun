package tr.olgun.kalangun;

import android.Manifest;
import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Instances;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class Picker extends Activity {
    private int widget, year;
    private LinearLayout page;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int generation;
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", TR);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", TR);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        widget = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        year = state == null ? LocalDate.now().getYear() : state.getInt("year");
        setResult(RESULT_CANCELED);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xfff1f6fa);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(18), dp(16) + insets.getSystemWindowInsetTop(), dp(18), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(page);
    }
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(0xff14263d);
        v.setPadding(0, dp(6), 0, dp(8)); page.addView(v);
        return v;
    }
    void button(LinearLayout row, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); row.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    void show() {
        int revision = ++generation;
        page.removeAllViews();
        label(widget == 0 ? "Kalan Gün" : "Etkinlik seç", 28);
        if (widget == 0) {
            label("Ana ekrana bir sayaç ekleyin. Her sayaç için ayrı bir takvim etkinliği seçebilirsiniz.", 17);
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            if (m.isRequestPinAppWidgetSupported()) button(page, "Ana ekrana widget ekle", () -> {
                m.requestPinAppWidget(new ComponentName(this, Counter.class), null, null);
            });
            label("Ana ekranda boş bir yere basılı tutup Widget’lar › Kalan Gün yolunu da kullanabilirsiniz. Etkinliği değiştirmek için çarka dokunun.", 16);
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            label("Etkinlikleri listelemek için takviminizi okuma izni gerekiyor.", 17);
            button(page, "Takvim izni ver", () -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 1));
            button(page, "Uygulama ayarlarını aç", () -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
            return;
        }
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER); page.addView(nav);
        if (year > LocalDate.now().getYear()) button(nav, "‹", () -> { year--; show(); });
        TextView y = new TextView(this); y.setText(String.valueOf(year)); y.setTextSize(21); nav.addView(y);
        button(nav, "›", () -> { year++; show(); });
        TextView status = label("Takvim okunuyor…", 16);
        final int selectedYear = year;
        worker.execute(() -> {
            ArrayList<Entry> items = new ArrayList<>();
            String error = null;
            LocalDate today = LocalDate.now();
            LocalDate first = LocalDate.of(selectedYear, 1, 1);
            LocalDate last = first.plusYears(1);
            LocalDate from = today.isAfter(first) ? today : first;
            Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri, from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            ContentUris.appendId(uri, last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            try (Cursor cursor = getContentResolver().query(uri.build(), new String[]{Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY},
                    Instances.VISIBLE + "=1", null, Instances.BEGIN + " ASC")) {
                if (cursor == null) throw new IllegalStateException();
                while (cursor.moveToNext()) {
                    Entry e = new Entry(cursor.getString(0), cursor.getLong(1), cursor.getInt(2) != 0);
                    LocalDate day = Counter.date(e.begin, e.all);
                    if (!day.isBefore(from) && day.isBefore(last)) items.add(e);
                }
            } catch (RuntimeException ex) { error = "Takvim okunamadı. Takvim iznini kontrol edin."; }
            final String problem = error;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || revision != generation) return;
                if (problem != null || items.isEmpty()) {
                    status.setText(problem != null ? problem : "Bu yıl için yaklaşan etkinlik yok.");
                    return;
                }
                page.removeView(status);
                ListView list = new ListView(this);
                list.setAdapter(new ArrayAdapter<Entry>(this, android.R.layout.simple_list_item_1, items));
                page.addView(list);
                list.setOnItemClickListener((parent, view, position, row) -> select(items.get(position)));
            });
        });
    }
    static class Entry {
        final String title; final long begin; final boolean all;
        Entry(String title, long begin, boolean all) {
            this.title = title == null || title.trim().isEmpty() ? "Adsız etkinlik" : title;
            this.begin = begin; this.all = all;
        }
        public String toString() {
            return title + "\n" + DAY.format(Counter.date(begin, all)) + (all ? " · Tüm gün" :
                " · " + CLOCK.format(Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())));
        }
    }
    void select(Entry e) {
        Counter.prefs(this, widget).edit().putString("title", e.title).putLong("begin", e.begin)
            .putBoolean("all", e.all).putLong("start", LocalDate.now().toEpochDay()).apply();
        Counter.draw(this, widget);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget));
        finish();
    }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] results) {
        super.onRequestPermissionsResult(code, p, results); show();
    }
    @Override public void onResume() { super.onResume(); if (page != null) show(); }
    @Override public void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putInt("year", year); }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}

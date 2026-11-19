package tr.olgun.kalangun;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;
import java.time.*;

public class Counter extends AppWidgetProvider {
    static SharedPreferences prefs(Context c, int id) {
        return c.getSharedPreferences("countdown_" + id, Context.MODE_PRIVATE);
    }
    static LocalDate date(long millis, boolean allDay) {
        return Instant.ofEpochMilli(millis).atZone(allDay ? ZoneOffset.UTC : ZoneId.systemDefault()).toLocalDate();
    }
    static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, Counter.class));
    }
    static void draw(Context c, int id) {
        SharedPreferences p = prefs(c, id);
        boolean ready = p.contains("begin");
        long today = LocalDate.now().toEpochDay();
        long end = date(p.getLong("begin", 0), p.getBoolean("all", false)).toEpochDay();
        long left = end - today;
        long span = Math.max(1, end - p.getLong("start", today));
        String title = p.getString("title", "Etkinlik seç");
        String value = !ready ? "+" : left > 0 ? String.valueOf(left) : left == 0 ? "Bugün" : "Bitti";
        Bitmap b = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        TextPaint pen = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        pen.setColor(0xF514263D);
        canvas.drawCircle(192, 192, 187, pen);
        RectF bounds = new RectF(19, 19, 365, 365);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(17);
        pen.setColor(0xFF374C60);
        canvas.drawOval(bounds, pen);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setColor(0xFF38D3FF);
        if (ready && left > 0) canvas.drawArc(bounds, -90, 360f * Math.min(1f, (float)left / span), false, pen);
        pen.setStyle(Paint.Style.FILL);
        pen.setTextAlign(Paint.Align.CENTER);
        pen.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pen.setTextSize(value.length() < 4 ? 105 : 75);
        pen.setColor(-1);
        canvas.drawText(value, 192, ready && left > 0 ? 190 : 205, pen);
        if (ready && left > 0) {
            pen.setTextSize(32);
            canvas.drawText("gün", 192, 234, pen);
        }
        pen.setTextSize(29);
        pen.setColor(0xFFE0EFF8);
        canvas.drawText(TextUtils.ellipsize(title, pen, 248, TextUtils.TruncateAt.END).toString(), 192, 284, pen);
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget);
        views.setImageViewBitmap(R.id.ring, b);
        views.setContentDescription(R.id.ring, title + ", " + value + (ready && left > 0 ? " gün" : ""));
        Intent intent = new Intent(c, Picker.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(R.id.ring, PendingIntent.getActivity(c, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        AppWidgetManager.getInstance(c).updateAppWidget(id, views);
    }
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) draw(c, id);
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        draw(c, id);
    }
    @Override public void onDeleted(Context c, int[] ids) {
        for (int id : ids) c.deleteSharedPreferences("countdown_" + id);
    }
    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String a = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_DATE_CHANGED.equals(a))
            for (int id : ids(c)) draw(c, id);
    }
}

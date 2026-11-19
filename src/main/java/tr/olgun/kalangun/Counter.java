package tr.olgun.kalangun;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.time.*;
import java.util.*;

public class Counter extends AppWidgetProvider {
    static final String PIN = "tr.olgun.kalangun.PIN_EVENT";
    static final int NUMBER = Color.BLACK, BACK = Color.WHITE, RING = 0xffe22938;
    static SharedPreferences index(Context c) { return c.getSharedPreferences("page", 0); }
    static SharedPreferences event(Context c,String key) { return c.getSharedPreferences("item_"+key,0); }
    static int[] widgets(Context c) { return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,Counter.class)); }
    static boolean owns(Context c,int id) {
        for(int i : widgets(c)) if(i==id) return true;
        return false;
    }
    static String linked(Context c,int id) {
        String ref=index(c).getString("widget_"+id,"");
        SharedPreferences old=c.getSharedPreferences("countdown_"+id,0);
        if(ref.isEmpty() && old.contains("begin")) {
            ref="legacy-"+id;
            event(c,ref).edit().putString("title",old.getString("title","Etkinlik"))
                .putLong("begin",old.getLong("begin",0)).putBoolean("all",old.getBoolean("all",false))
                .putLong("start",old.getLong("start",LocalDate.now().toEpochDay())).apply();
            index(c).edit().putString("widget_"+id,ref).putBoolean("item_"+ref,true).apply();
            c.deleteSharedPreferences("countdown_"+id);
        }
        return ref;
    }
    static String save(Context c,String key,String title,long time,boolean all) {
        if(key.isEmpty()) key=UUID.randomUUID().toString();
        event(c,key).edit().putString("title",title).putLong("begin",time).putBoolean("all",all)
            .putLong("start",LocalDate.now().toEpochDay()).apply();
        index(c).edit().putBoolean("item_"+key,true).apply();
        refresh(c); return key;
    }
    static void bind(Context c,int id,String key) {
        if(!owns(c,id) || !event(c,key).contains("begin")) return;
        index(c).edit().putString("widget_"+id,key).apply(); draw(c,id);
    }
    static void remove(Context c,String key) {
        for(int id:widgets(c)) if(linked(c,id).equals(key)) index(c).edit().remove("widget_"+id).apply();
        c.deleteSharedPreferences("item_"+key);
        index(c).edit().remove("item_"+key).apply(); refresh(c);
    }
    static LocalDate day(SharedPreferences e) {
        return Instant.ofEpochMilli(e.getLong("begin",0)).atZone(e.getBoolean("all",false)?ZoneOffset.UTC:ZoneId.systemDefault()).toLocalDate();
    }
    static Bitmap image(SharedPreferences e) {
        boolean ready=e.contains("begin");
        long today=LocalDate.now().toEpochDay(), end=day(e).toEpochDay(), left=Math.max(0,end-today);
        boolean done=ready && (e.getBoolean("all",false)?today>=end:System.currentTimeMillis()>=e.getLong("begin",0));
        int ring=done?0xffff3030:e.getInt("ring",RING);
        Bitmap b=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(done?0xffbc141e:e.getInt("back",BACK)); canvas.drawCircle(180,180,177,p);
        RectF bounds=new RectF(12,12,348,348);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(15); p.setColor(ring); p.setAlpha(55); canvas.drawOval(bounds,p);
        p.setAlpha(255); p.setStrokeCap(Paint.Cap.ROUND);
        long span=Math.max(1,end-e.getLong("start",today));
        float fraction=done?1:Math.min(1f,(float)left/span);
        canvas.drawArc(bounds,-90,360*fraction,false,p);
        String value=ready?Long.toString(left):"+";
        p.setStyle(Paint.Style.FILL); p.setColor(done?Color.WHITE:e.getInt("number",NUMBER));
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(142);
        if(p.measureText(value)>255) p.setTextSize(142*255/p.measureText(value));
        canvas.drawText(value,180,180-(p.ascent()+p.descent())/2,p);
        return b;
    }
    static void draw(Context c,int id) {
        String key=linked(c,id);
        SharedPreferences e=event(c,key);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        v.setImageViewBitmap(R.id.ring,image(e));
        String title=e.getString("title","Etkinlik seç");
        v.setTextViewText(R.id.caption,title);
        v.setContentDescription(R.id.ring,title+(e.contains("begin")?", "+Math.max(0,day(e).toEpochDay()-LocalDate.now().toEpochDay())+" gün":""));
        Intent open=new Intent(c,Picker.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        v.setOnClickPendingIntent(R.id.panel,PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        AppWidgetManager.getInstance(c).updateAppWidget(id,v);
    }
    static void refresh(Context c) { for(int id:widgets(c)) draw(c,id); }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){ for(int id:ids) draw(c,id); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){ draw(c,id); }
    @Override public void onDeleted(Context c,int[] ids){ for(int id:ids){index(c).edit().remove("widget_"+id).apply(); c.deleteSharedPreferences("countdown_"+id);} }
    @Override public void onReceive(Context c,Intent i){
        super.onReceive(c,i);
        if(PIN.equals(i.getAction())) bind(c,i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0),i.getStringExtra("key"));
        else if(Intent.ACTION_DATE_CHANGED.equals(i.getAction())||Intent.ACTION_TIME_CHANGED.equals(i.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())) refresh(c);
    }
}

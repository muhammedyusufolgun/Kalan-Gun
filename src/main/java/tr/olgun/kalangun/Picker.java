package tr.olgun.kalangun;

import android.Manifest;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.view.*;
import android.widget.*;
import java.util.*;

public class Picker extends Activity {
    static final int PERMISSION=32;
    int widget; String target="", resizing="";
    LinearLayout page; boolean configuring;
    final Handler timer=new Handler(Looper.getMainLooper());
    final Runnable redraw=new Runnable(){public void run(){show();timer.postDelayed(this,60000);}};
    int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
    void notice(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    SharedPreferences idx(){return Counter.index(this);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent i=getIntent(); widget=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0);
        if(!Counter.owns(this,widget)) widget=0;
        configuring=AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(i.getAction());
        if(state!=null){target=state.getString("target","");resizing=state.getString("size","");}
        setResult(RESULT_CANCELED);
        ScrollView scroll=new ScrollView(this); page=new LinearLayout(this); page.setOrientation(1);
        page.setGravity(Gravity.CENTER_HORIZONTAL); scroll.addView(page); scroll.setBackgroundColor(0xfff3f6fa);
        scroll.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(dp(12),dp(10)+in.getSystemWindowInsetTop(),dp(12),dp(12)+in.getSystemWindowInsetBottom());return in;});
        setContentView(scroll);
        for(int id:Counter.widgets(this)) Counter.linked(this,id);
        show();
        if(state!=null) return;
        if(Intent.ACTION_SEND.equals(i.getAction())){
            try {
                CalendarEvent e=CalendarEvent.shared(this,i);
                accept(e);
            } catch(Exception ex){notice("Etkinlik okunamadı. Takvimde Paylaş menüsünden iCalendar (.ics) dosyasını seçin.");}
        }else if(widget==0 && Intent.ACTION_MAIN.equals(i.getAction())) calendar("");
        else if(widget!=0){
            String existing=Counter.linked(this,widget);
            if(existing.isEmpty()) calendar(""); else if(!configuring)menu(existing);
        }
    }
    void calendar(String key){
        target=key;
        if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},PERMISSION);
            return;
        }
        if(getFragmentManager().findFragmentByTag("days")==null)
            new ThirtyDays().show(getFragmentManager(),"days");
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
        super.onRequestPermissionsResult(code,names,results);
        if(code!=PERMISSION)return;
        if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)calendar(target);
        else new AlertDialog.Builder(this).setMessage("30 günlük görünüm için takvim okuma izni gerekiyor.")
            .setNegativeButton("Kapat",null).setPositiveButton("Ayarlar",(d,w)->startActivity(new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }
    void applyWidget(String key){
        if(widget!=0){
            Counter.bind(this,widget,key);
            if(configuring)finishConfiguration();
        } else pin(key);
    }
    void accept(CalendarEvent e){
        boolean changing=!target.isEmpty();
        String key=Counter.save(this,target,e.title,e.time,e.all);
        target="";resizing="";show();
        if(!changing || widget!=0)applyWidget(key);
    }
    void finishConfiguration(){
        setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widget));finish();
    }
    Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    List<String> keys(){
        ArrayList<String> list=new ArrayList<>();
        for(String key:idx().getAll().keySet()) if(key.startsWith("item_"))list.add(key.substring(5));
        list.sort(Comparator.comparingLong(k->Counter.event(this,k).getLong("begin",0)));return list;
    }
    void show(){
        page.removeAllViews();
        page.addView(button("Takvim",()->calendar("")));
        for(String key:keys()) addCard(key);
    }
    void addCard(String key){
        SharedPreferences e=Counter.event(this,key);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);page.addView(card);
        FrameLayout frame=new FrameLayout(this);
        int initial=clamp(e.getInt("size",148)); card.addView(frame,new LinearLayout.LayoutParams(dp(initial),dp(initial)));
        ImageView image=new ImageView(this);image.setImageBitmap(Counter.image(e));frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        String title=e.getString("title","Etkinlik");image.setContentDescription(title);
        TextView caption=new TextView(this);caption.setText(title);caption.setGravity(Gravity.CENTER);
        caption.setTextColor(0xff14263d);caption.setTextSize(16);card.addView(caption);card.setPadding(0,dp(8),0,dp(8));
        View.OnLongClickListener hold=v->{resizing=key;show();menu(key);return true;};
        for(View v:new View[]{image,caption}){
            v.setOnLongClickListener(hold); v.setOnClickListener(w->{
                applyWidget(key);
            });
        }
        if(resizing.equals(key)) for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}) corner(frame,e,x,y);
    }
    int clamp(int size){
        int max=Math.min(360,Math.round((getResources().getDisplayMetrics().widthPixels-dp(24))/getResources().getDisplayMetrics().density));
        return Math.max(68,Math.min(max,size));
    }
    void corner(FrameLayout f,SharedPreferences e,int x,int y){
        TextView grip=new TextView(this);grip.setGravity(Gravity.CENTER);grip.setTextColor(0xff075c91);
        grip.setTextSize(21);grip.setText(x==y?"⤡":"⤢");grip.setContentDescription("Boyutlandır");
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(36),dp(36),(x<0?Gravity.LEFT:Gravity.RIGHT)|(y<0?Gravity.TOP:Gravity.BOTTOM));
        f.addView(grip,lp);
        grip.setOnTouchListener(new View.OnTouchListener(){
            float sx,sy;int base,size;
            public boolean onTouch(View v,android.view.MotionEvent m){
                switch(m.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        f.getParent().requestDisallowInterceptTouchEvent(true);
                        sx=m.getRawX();sy=m.getRawY();base=Math.round(f.getWidth()/getResources().getDisplayMetrics().density);size=base;return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=x*(m.getRawX()-sx),dy=y*(m.getRawY()-sy);
                        size=clamp(base+Math.round((Math.abs(dx)>Math.abs(dy)?dx:dy)/getResources().getDisplayMetrics().density));
                        ViewGroup.LayoutParams params=f.getLayoutParams();params.width=params.height=dp(size);f.setLayoutParams(params);return true;
                    case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                        e.edit().putInt("size",size).apply(); f.getParent().requestDisallowInterceptTouchEvent(false);return true;
                    default:return false;
                }
            }
        });
    }
    void menu(String key){
        SharedPreferences e=Counter.event(this,key);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(e.getString("title","Etkinlik")).create();
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(12),0,dp(12),dp(8));box.setOrientation(1);
        box.addView(button("Takvim",()->{dialog.dismiss();calendar(key);}));
        box.addView(button("Düzenleme",()->{dialog.dismiss();colors(key);}));
        box.addView(button("Boyutlandır",()->{dialog.dismiss();resizing=key;show();}));
        if(AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported()) box.addView(button("Ana ekrana ekle",()->{dialog.dismiss();pin(key);}));
        Button del=button("Sil",()->{dialog.dismiss();delete(key,false);});del.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffba202b));del.setTextColor(-1);box.addView(del);
        dialog.setView(box);dialog.show();
    }
    void colors(String key){
        String[] names={"Sayı Rengi","Arkaplan Rengi","Çember Rengi"};
        String[] props={"number","back","ring"};
        new AlertDialog.Builder(this).setTitle("Düzenleme").setItems(names,(d,n)->palette(key,props[n],names[n])).show();
    }
    void palette(String key,String prop,String name){
        String[] labels={"Beyaz","Siyah","Lacivert","Mavi","Turkuaz","Yeşil","Sarı","Turuncu","Kırmızı","Mor","Gri"};
        int[] values={-1,0xff111111,Counter.BACK,0xff2474da,Counter.RING,0xff229955,0xffffce28,0xffff8728,0xffe22938,0xff8946ce,0xff778492};
        new AlertDialog.Builder(this).setTitle(name).setItems(labels,(d,n)->{
            Counter.event(this,key).edit().putInt(prop,values[n]).apply();Counter.refresh(this);show();
        }).show();
    }
    void delete(String key,boolean confirmed){
        new AlertDialog.Builder(this).setTitle(confirmed?"Silmeyi onayla":"Etkinliği sil")
            .setMessage(confirmed?"Kalan Gün sayfasındaki bu sayaç silinsin mi?":Counter.event(this,key).getString("title","Etkinlik")+" silinsin mi?")
            .setNegativeButton("Vazgeç",null).setPositiveButton(confirmed?"Sil":"Devam",(d,w)->{
                if(confirmed){Counter.remove(this,key);resizing="";show();}else delete(key,true);
            }).show();
    }
    void pin(String key){
        if(!AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported()){
            notice("Ana ekrana basılı tutup Widget’lar → Kalan Gün seçin ve etkinliğe dokunun.");return;
        }
        Intent i=new Intent(this,Counter.class).setAction(Counter.PIN).setData(Uri.parse("kalangun://pin/"+UUID.randomUUID())).putExtra("key",key);
        int flags=PendingIntent.FLAG_ONE_SHOT;
        if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent callback=PendingIntent.getBroadcast(this,0,i,flags);
        if(!AppWidgetManager.getInstance(this).requestPinAppWidget(new ComponentName(this,Counter.class),null,callback)){
            callback.cancel();notice("Ana ekranın Widget’lar menüsünden ekleyin.");
        }
    }
    @Override protected void onResume(){super.onResume();redraw.run();}
    @Override protected void onPause(){timer.removeCallbacks(redraw);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putString("target",target);b.putString("size",resizing);
    }
}

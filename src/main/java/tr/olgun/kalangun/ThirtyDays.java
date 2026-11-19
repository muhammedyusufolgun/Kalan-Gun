package tr.olgun.kalangun;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Instances;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ThirtyDays extends DialogFragment {
    static final Locale TR=Locale.forLanguageTag("tr");
    static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("d MMM",TR);
    long first, chosen;
    LinearLayout body;
    Map<Long,List<CalendarEvent>> events=new HashMap<>();
    ExecutorService reader;
    int generation;
    String status;
    List<ActiveCalendar> options=Collections.emptyList();
    ActiveCalendar active;
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public Dialog onCreateDialog(Bundle state){
        first=state==null?LocalDate.now().toEpochDay():state.getLong("first");
        chosen=state==null?first:state.getLong("chosen");
        reader=Executors.newSingleThreadExecutor();
        ScrollView sc=new ScrollView(getActivity());body=new LinearLayout(getActivity());
        body.setOrientation(1);body.setPadding(dp(10),dp(6),dp(10),dp(8));sc.addView(body);
        TextView heading=text("30 günlük takvim",20);
        heading.setPadding(dp(12),dp(12),dp(12),dp(12));
        heading.setBackgroundColor(0xffadede6);
        heading.setTextColor(android.graphics.Color.BLACK);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        AlertDialog dialog=new AlertDialog.Builder(getActivity()).setCustomTitle(heading).setView(sc)
            .setNegativeButton("Kapat",null).create();
        load(); return dialog;
    }
    void createEvent(){
        if(getFragmentManager().findFragmentByTag("create-event")!=null)return;
        if(active==null||!active.writable)return;
        NewEvent form=new NewEvent();Bundle args=new Bundle();args.putLong("date",chosen);args.putLong("calendar",active.id);args.putString("calendarName",active.label());
        form.setArguments(args);form.show(getFragmentManager(),"create-event");
    }
    void load(){
        final long from=first; final int version=++generation;
        Context context=getActivity().getApplicationContext();
        active=null;options=Collections.emptyList();events.clear();status="Etkinlikler yükleniyor…";render();
        reader.execute(()->{
            Map<Long,List<CalendarEvent>> result=new HashMap<>();String error=null;
            LocalDate start=LocalDate.ofEpochDay(from);
            Uri.Builder uri=Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri,start.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            ContentUris.appendId(uri,start.plusDays(31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            List<ActiveCalendar> calendars=Collections.emptyList(); ActiveCalendar selected=null;
            try{
                calendars=ActiveCalendar.available(context);
                selected=ActiveCalendar.selected(context,calendars);
                if(selected==null)error=calendars.isEmpty()?"Etkin bir takvim bulunamadı.":"Varsayılan hesabınızın takvimini seçin.";
                else try(Cursor rows=context.getContentResolver().query(uri.build(),new String[]{Instances.TITLE,Instances.BEGIN,Instances.ALL_DAY},
                    Instances.CALENDAR_ID+"=?",new String[]{Long.toString(selected.id)},Instances.BEGIN+" ASC")){
                if(rows==null)throw new IllegalStateException();
                while(rows.moveToNext()){
                    CalendarEvent e=new CalendarEvent();String title=rows.getString(0);
                    e.title=title==null||title.isEmpty()?"Etkinlik":title;e.time=rows.getLong(1);e.all=rows.getInt(2)!=0;
                    long day=Instant.ofEpochMilli(e.time).atZone(e.all?ZoneOffset.UTC:ZoneId.systemDefault()).toLocalDate().toEpochDay();
                    if(day>=from&&day<from+30) result.computeIfAbsent(day,k->new ArrayList<>()).add(e);
                }
            }
            }catch(RuntimeException ex){error="Takvim okunamadı. Takvim iznini kontrol edin.";}
            final String message=error;final List<ActiveCalendar> choices=calendars;final ActiveCalendar current=selected;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                if(!isAdded()||body==null||version!=generation)return;
                active=current;options=choices;events=result;status=message;render();
            });
        });
    }
    TextView text(String s,int size){TextView v=new TextView(getActivity());v.setText(s);v.setTextSize(size);v.setTextColor(0xff172b42);v.setGravity(Gravity.CENTER);return v;}
    void step(int n){first+=n;chosen=first;load();}
    void render(){
        body.removeAllViews();LinearLayout nav=new LinearLayout(getActivity());body.addView(nav);
        Button prev=new Button(getActivity());prev.setText("‹");prev.setContentDescription("Önceki 30 gün");prev.setOnClickListener(v->step(-30));nav.addView(prev,dp(48),dp(48));
        nav.addView(text(DATE.format(LocalDate.ofEpochDay(first))+" – "+DATE.format(LocalDate.ofEpochDay(first+29))+" "+LocalDate.ofEpochDay(first+29).getYear(),16),new LinearLayout.LayoutParams(0,dp(48),1));
        Button next=new Button(getActivity());next.setText("›");next.setContentDescription("Sonraki 30 gün");next.setOnClickListener(v->step(30));nav.addView(next,dp(48),dp(48));
        if(options.size()>1){
            Button account=new Button(getActivity());account.setAllCaps(false);
            account.setText(active==null?"Varsayılan takvimi seç":active.label());body.addView(account);
            account.setOnClickListener(v->{
                List<ActiveCalendar> choices=new ArrayList<>(options);String[] names=new String[choices.size()];
                for(int n=0;n<choices.size();n++)names[n]=choices.get(n).label();
                new AlertDialog.Builder(getActivity()).setTitle("Varsayılan takvim").setItems(names,(dialog,n)->{
                    choices.get(n).remember(getActivity());load();
                }).show();
            });
        } else if(active!=null)body.addView(text(active.label(),14));
        LinearLayout labels=new LinearLayout(getActivity());body.addView(labels);
        for(String name:new String[]{"Pt","Sa","Ça","Pe","Cu","Ct","Pa"})labels.addView(text(name,13),new LinearLayout.LayoutParams(0,dp(26),1));
        int offset=LocalDate.ofEpochDay(first).getDayOfWeek().getValue()-1;
        LinearLayout row=null;
        for(int i=0;i<((30+offset+6)/7)*7;i++){
            if(i%7==0){row=new LinearLayout(getActivity());body.addView(row);}
            final long day=first+i-offset; boolean valid=day>=first&&day<first+30;
            TextView cell=text("",16);
            row.addView(cell,new LinearLayout.LayoutParams(0,dp(39),1));
            if(!valid)continue;
            boolean marked=events.containsKey(day);
            cell.setText(LocalDate.ofEpochDay(day).getDayOfMonth()+(marked?" •":""));
            cell.setContentDescription(DATE.format(LocalDate.ofEpochDay(day))+(marked?", etkinlik var":""));
            if(day==chosen){cell.setBackgroundColor(0xff075985);cell.setTextColor(-1);}
            else if(marked){cell.setBackgroundColor(0xffd9ecfa);}
            cell.setOnClickListener(v->{chosen=day;render();});
        }
        body.addView(text(DATE.format(LocalDate.ofEpochDay(chosen)),16));
        Button create=new Button(getActivity());create.setAllCaps(false);create.setText("Yeni Etkinlik Oluştur");create.setEnabled(active!=null&&active.writable);
        create.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffbde4ff));
        create.setOnClickListener(v->createEvent());body.addView(create);
        List<CalendarEvent> items=events.get(chosen);
        if(status!=null || items==null){body.addView(text(status!=null?status:"Bu gün için etkinlik yok.",15));return;}
        for(CalendarEvent e:items){
            Button b=new Button(getActivity());b.setAllCaps(false);
            String when=e.all?"Tüm gün":DateTimeFormatter.ofPattern("HH:mm",TR).format(Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()));
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffc9efce));
            b.setText(when+" · "+e.title);body.addView(b);
            b.setOnClickListener(v->{Picker p=(Picker)getActivity();dismiss();p.accept(e);});
        }
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putLong("first",first);b.putLong("chosen",chosen);}
    @Override public void onDestroy(){generation++;if(reader!=null)reader.shutdownNow();body=null;super.onDestroy();}
}

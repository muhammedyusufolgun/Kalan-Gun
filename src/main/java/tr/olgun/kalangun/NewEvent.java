package tr.olgun.kalangun;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NewEvent extends DialogFragment {
    private EditText title;
    private long calendarId;
    private CheckBox allDay;
    private Button time;
    private int hour,minute;
    private long date;
    @Override public Dialog onCreateDialog(Bundle state){
        date=getArguments().getLong("date");hour=state==null?9:state.getInt("hour");
        minute=state==null?0:state.getInt("minute");
        Activity a=getActivity();
        LinearLayout form=new LinearLayout(a);form.setOrientation(1);
        int pad=Math.round(16*getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
        title=new EditText(a);title.setSingleLine();title.setHint("Etkinlik adı");form.addView(title);
        calendarId=getArguments().getLong("calendar",-1);
        TextView calendarName=new TextView(a);calendarName.setText(getArguments().getString("calendarName"));form.addView(calendarName);
        allDay=new CheckBox(a);allDay.setText("Tüm gün");form.addView(allDay);
        time=new Button(a);form.addView(time);updateTime();
        time.setOnClickListener(v->new TimePickerDialog(a,(view,h,m)->{hour=h;minute=m;updateTime();},hour,minute,true).show());
        allDay.setOnCheckedChangeListener((b,checked)->time.setEnabled(!checked));
        if(state!=null){title.setText(state.getString("name"));allDay.setChecked(state.getBoolean("all"));
        }
        String day=DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag("tr")).format(LocalDate.ofEpochDay(date));
        return new AlertDialog.Builder(a).setTitle(day).setView(form).setPositiveButton("Takvime kaydet",null)
            .setNegativeButton("Vazgeç",null).create();
    }
    void updateTime(){time.setText(String.format(Locale.forLanguageTag("tr"),"Saat %02d:%02d",hour,minute));}
    @Override public void onStart(){
        super.onStart();Button save=((AlertDialog)getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(calendarId>=0);save.setOnClickListener(v->{
            if(title.getText().toString().trim().isEmpty()){title.setError("Etkinlik adını yazın.");return;}
            if(getActivity().checkSelfPermission(Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR},71);
            else saveEvent();
        });
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=71||!isAdded())return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)saveEvent();
        else Toast.makeText(getActivity(),"Takvime kayıt için yazma izni gerekiyor.",Toast.LENGTH_LONG).show();
    }
    void saveEvent(){
        boolean all=allDay.isChecked();ZoneId zone=all?ZoneOffset.UTC:ZoneId.systemDefault();LocalDate day=LocalDate.ofEpochDay(date);
        ZonedDateTime start=all?day.atStartOfDay(zone):day.atTime(hour,minute).atZone(zone);
        CalendarEvent e=new CalendarEvent();e.title=title.getText().toString().trim();e.time=start.toInstant().toEpochMilli();e.all=all;
        ContentValues values=new ContentValues();values.put(Events.CALENDAR_ID,calendarId);
        values.put(Events.TITLE,e.title);values.put(Events.DTSTART,e.time);
        values.put(Events.DTEND,(all?start.plusDays(1):start.plusHours(1)).toInstant().toEpochMilli());
        values.put(Events.ALL_DAY,all?1:0);values.put(Events.EVENT_TIMEZONE,all?"UTC":zone.getId());
        try{
            ActiveCalendar current=ActiveCalendar.selected(getActivity(),ActiveCalendar.available(getActivity()));
            if(current==null||current.id!=calendarId||!current.writable){
                Toast.makeText(getActivity(),"Kayıt takvimi değişti. Takvimi yeniden açın.",Toast.LENGTH_LONG).show();return;
            }
            if(getActivity().getContentResolver().insert(Events.CONTENT_URI,values)==null)throw new IllegalStateException();
        }catch(RuntimeException ex){Toast.makeText(getActivity(),"Etkinlik kaydedilemedi. Takvim iznini ve seçilen takvimi kontrol edin.",Toast.LENGTH_LONG).show();return;}
        Picker p=(Picker)getActivity();DialogFragment days=(DialogFragment)getFragmentManager().findFragmentByTag("days");
        dismiss();if(days!=null)days.dismiss();p.accept(e);
    }
    @Override public void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putString("name",title.getText().toString());b.putBoolean("all",allDay.isChecked());
        b.putInt("hour",hour);b.putInt("minute",minute);
        if(!ids.isEmpty())b.putLong("calendar",ids.get(calendars.getSelectedItemPosition()));
    }
}

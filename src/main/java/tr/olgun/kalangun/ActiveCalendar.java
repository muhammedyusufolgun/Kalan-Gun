package tr.olgun.kalangun;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;
import java.util.*;

final class ActiveCalendar {
    final long id;
    final String name, account, type;
    final boolean primary, writable;

    ActiveCalendar(Cursor row) {
        id=row.getLong(0); name=row.getString(1)==null?"Takvim":row.getString(1); account=row.getString(2);
        type=row.getString(3); primary=row.getInt(4)!=0;
        writable=row.getInt(5)>=Calendars.CAL_ACCESS_CONTRIBUTOR;
    }
    String label(){return name.equals(account)?name:name+" · "+account;}
    static List<ActiveCalendar> available(Context c){
        List<ActiveCalendar> list=new ArrayList<>();
        String[] columns={Calendars._ID,Calendars.CALENDAR_DISPLAY_NAME,Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,Calendars.IS_PRIMARY,Calendars.CALENDAR_ACCESS_LEVEL};
        try(Cursor rows=c.getContentResolver().query(Calendars.CONTENT_URI,columns,
                Calendars.VISIBLE+"=1 AND "+Calendars.SYNC_EVENTS+"=1",null,Calendars._ID+" ASC")){
            if(rows==null)throw new IllegalStateException();
            while(rows.moveToNext())list.add(new ActiveCalendar(rows));
        }
        List<ActiveCalendar> primaries=new ArrayList<>();
        for(ActiveCalendar a:list)if(a.primary)primaries.add(a);
        return primaries.isEmpty()?list:primaries;
    }
    static ActiveCalendar selected(Context c,List<ActiveCalendar> options){
        if(options.size()==1)return options.get(0);
        SharedPreferences pref=c.getSharedPreferences("calendar_choice",0);
        for(ActiveCalendar a:options)if(a.id==pref.getLong("id",-1)
            && Objects.equals(a.account,pref.getString("account",null))
            && Objects.equals(a.type,pref.getString("type",null)))return a;
        return null;
    }
    void remember(Context c){c.getSharedPreferences("calendar_choice",0).edit().putLong("id",id)
        .putString("account",account).putString("type",type).apply();}
}

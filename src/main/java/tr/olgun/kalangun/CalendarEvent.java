package tr.olgun.kalangun;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

class CalendarEvent {
    String title="Etkinlik"; long time; boolean all;
    static CalendarEvent picked(Context c, Intent i) throws IOException {
        Uri uri=i.getData();
        if(uri==null || !"content".equals(uri.getScheme()) || !CalendarContract.AUTHORITY.equals(uri.getAuthority())) throw new IOException();
        try(Cursor row=c.getContentResolver().query(uri,new String[]{"title","dtstart","allDay"},null,null,null)){
            if(row==null || !row.moveToFirst() || row.isNull(1)) throw new IOException();
            CalendarEvent e=new CalendarEvent();
            if(row.getString(0)!=null) e.title=row.getString(0);
            e.time=i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,row.getLong(1));
            e.all=row.getInt(2)!=0; return e;
        }
    }
    static CalendarEvent shared(Context c, Intent i) throws IOException {
        Uri uri=i.getParcelableExtra(Intent.EXTRA_STREAM);
        if(uri==null && i.getClipData()!=null && i.getClipData().getItemCount()>0) uri=i.getClipData().getItemAt(0).getUri();
        String data;
        if(uri!=null && "content".equals(uri.getScheme())){
            try(InputStream in=c.getContentResolver().openInputStream(uri); ByteArrayOutputStream b=new ByteArrayOutputStream()){
                if(in==null) throw new IOException();
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))!=-1){if(b.size()+n>1048576) throw new IOException(); b.write(buf,0,n);}
                data=b.toString("UTF-8");
            }
        } else { data=i.getStringExtra(Intent.EXTRA_TEXT); }
        if(data==null || data.length()>1048576) throw new IOException();
        return parse(data);
    }
    static CalendarEvent parse(String data) throws IOException {
        CalendarEvent e=new CalendarEvent(); boolean inside=false, found=false; int depth=0;
        for(String line:data.replace("\r\n","\n").replaceAll("\n[ \\t]","").split("\n")){
            if(!inside){ if(line.trim().equalsIgnoreCase("BEGIN:VEVENT")) inside=true; continue; }
            if(line.trim().equalsIgnoreCase("END:VEVENT")) break;
            if(line.startsWith("BEGIN:")){depth++; continue;}
            if(line.startsWith("END:")){depth--; continue;}
            if(depth!=0) continue;
            int split=line.indexOf(':'); if(split<0) continue;
            String[] fields=line.substring(0,split).split(";");
            String val=line.substring(split+1).trim();
            if(fields[0].equalsIgnoreCase("SUMMARY") && !val.isEmpty())
                e.title=val.replace("\\n"," ").replace("\\N"," ").replace("\\, ",", ").replace("\\,",",").replace("\\;",";").replace("\\\\","\\");
            if(!fields[0].equalsIgnoreCase("DTSTART")) continue;
            e.all=val.matches("[0-9]{8}");
            if(e.all) e.time=LocalDate.parse(val,DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            else {
                ZoneId zone=ZoneId.systemDefault();
                for(String f:fields) if(f.regionMatches(true,0,"TZID=",0,5)) zone=ZoneId.of(f.substring(5).replace("\"",""));
                if(val.endsWith("Z")){zone=ZoneOffset.UTC;val=val.substring(0,val.length()-1);}
                DateTimeFormatter fmt=DateTimeFormatter.ofPattern(val.length()==13?"yyyyMMdd'T'HHmm":"yyyyMMdd'T'HHmmss",Locale.ROOT);
                e.time=LocalDateTime.parse(val,fmt).atZone(zone).toInstant().toEpochMilli();
            }
            found=true;
        }
        if(!found) throw new IOException(); return e;
    }
}

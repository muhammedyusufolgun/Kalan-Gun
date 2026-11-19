package tr.olgun.kalangun;

import org.junit.Test;
import java.io.IOException;
import java.time.Instant;
import static org.junit.Assert.*;

public class CalendarEventTest {
    @Test public void importedAllDayUsesCalendarDate() throws Exception {
        CalendarEvent event=CalendarEvent.parse("BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:20270101\r\nSUMMARY:Yeni \r\n yıl\r\nEND:VEVENT");
        assertTrue(event.all);
        assertEquals("Yeni yıl",event.title);
        assertEquals(Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(),event.time);
    }
    @Test public void istanbulTimeAndNestedReminder() throws Exception {
        CalendarEvent event=CalendarEvent.parse("BEGIN:VEVENT\nDTSTART;TZID=Europe/Istanbul:20270203T093000\nSUMMARY:Toplantı\nBEGIN:VALARM\nSUMMARY:Alarm\nEND:VALARM\nEND:VEVENT");
        assertFalse(event.all);
        assertEquals("Toplantı",event.title);
        assertEquals(Instant.parse("2027-02-03T06:30:00Z").toEpochMilli(),event.time);
    }
    @Test(expected=IOException.class) public void rejectsMissingStart() throws Exception {
        CalendarEvent.parse("BEGIN:VEVENT\nSUMMARY:Etkinlik\nEND:VEVENT");
    }
}

"""Exercise the release APK with a real Calendar Provider and launcher widget."""
import datetime
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

APP = "tr.olgun.kalangun"
TITLE = "KalanGunWidgetTest"
TODAY = datetime.datetime.now(datetime.timezone.utc).date()
DAY = TODAY + datetime.timedelta(days=2)

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)

def shell(*args):
    return adb("shell", shlex.join(args))

def content(action, uri, fields):
    args = ["content", action, "--uri", uri]
    for item in fields:
        args.extend(["--bind", item])
    output = shell(*args)
    if "Exception" in output or "Error" in output:
        raise RuntimeError(output)

def tree():
    shell("uiautomator", "dump", "/sdcard/kalan-ui.xml")
    return list(ET.fromstring(shell("cat", "/sdcard/kalan-ui.xml")).iter("node"))

def find(predicate):
    last = []
    for _ in range(5):
        last = tree()
        matches = [node for node in last if predicate(node)]
        if matches:
            return matches[0]
        time.sleep(.5)
    raise AssertionError([(n.get("text"),n.get("content-desc"),n.get("resource-id")) for n in last])

def tap(node):
    x1,y1,x2,y2 = map(int, re.findall(r"\d+",node.get("bounds")))
    shell("input","tap",str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.6)

def select_day(day):
    tap(find(lambda n: n.get("text")==str(day.day)+" •"))

adb("root")
adb("wait-for-device")
base = "content://com.android.calendar"
account = "KalanGunWidgetTest"
sync = "?caller_is_syncadapter=true&account_name="+account+"&account_type=LOCAL"
content("insert",base+"/calendars"+sync,[
    "account_name:s:"+account,"account_type:s:LOCAL","name:s:"+account,
    "calendar_displayName:s:"+account,"visible:i:1","sync_events:i:1",
    "calendar_access_level:i:700","ownerAccount:s:"+account,"calendar_timezone:s:UTC"])
row = shell("content","query","--uri",base+"/calendars","--projection","_id","--where","name='"+account+"'")
cal = re.search(r"_id=(\d+)",row).group(1)
start = int(datetime.datetime.combine(DAY,datetime.time.min,datetime.timezone.utc).timestamp()*1000)
content("insert",base+"/events",["calendar_id:i:"+cal,"title:s:"+TITLE,
    "dtstart:l:"+str(start),"dtend:l:"+str(start+86400000),"allDay:i:1","eventTimezone:s:UTC"])
shell("pm","grant",APP,"android.permission.READ_CALENDAR")
shell("am","start","-W","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-n",APP+"/.Picker")
find(lambda n:n.get("text")=="30 günlük takvim")
days = [n for n in tree() if re.fullmatch(r"\d+( •)?",n.get("text",""))]
assert len(days)==30,len(days)
tap(find(lambda n:n.get("content-desc")=="Sonraki 30 gün"))
tap(find(lambda n:n.get("content-desc")=="Önceki 30 gün"))
select_day(DAY)
tap(find(lambda n:TITLE in n.get("text","")))
def pin_button(n):
    label=n.get("text","").lower()
    rid=n.get("resource-id","")
    return n.get("package")!=APP and ("add" in label or "add_button" in rid or rid.endswith("/button1"))
tap(find(pin_button))
shell("input","keyevent","KEYCODE_HOME")
badge=find(lambda n: TITLE in n.get("text","") or TITLE in n.get("content-desc",""))
print("Calendar selection created a launcher widget:",badge.attrib,flush=True)
tap(badge)
find(lambda n:n.get("text")=="Düzenleme")

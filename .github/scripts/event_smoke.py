import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
import re

def shell(*args):
    return subprocess.check_output(["adb", "shell", shlex.join(args)], text=True)

event = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART;VALUE=DATE:20200101\nSUMMARY:Deneme\nEND:VEVENT\nEND:VCALENDAR"
shell("am","start","-W","-a","android.intent.action.SEND","-n","tr.olgun.kalangun/.Picker","-t","text/calendar","--es","android.intent.extra.TEXT",event)

def nodes():
    shell("uiautomator","dump","/sdcard/kalan-test.xml")
    return list(ET.fromstring(shell("cat","/sdcard/kalan-test.xml")).iter("node"))

def find(label):
    for attempt in range(4):
        for n in nodes():
            if label in (n.get("resource-id"), n.get("text"), n.get("content-desc")):
                return n
        time.sleep(1)
    raise RuntimeError("Ekranda bulunamadı: "+label)

def touch(label, hold=False):
    x1,y1,x2,y2=map(int,re.findall(r"\d+",find(label).get("bounds")))
    x,y=str((x1+x2)//2),str((y1+y2)//2)
    if hold: shell("input","swipe",x,y,x,y,"850")
    else: shell("input","tap",x,y)
    time.sleep(.5)

touch("Deneme", True)
touch("Düzenleme")
touch("Sayı Rengi")
touch("Mavi")
touch("Deneme", True)
touch("Boyutlandır")
find("Boyutlandır")
touch("Deneme", True)
touch("Sil")
touch("android:id/button1")
touch("android:id/button2")
touch("Deneme", True)
touch("Sil")
touch("android:id/button1")
touch("android:id/button1")
assert all(n.get("text")!="Deneme" and n.get("content-desc")!="Deneme" for n in nodes())

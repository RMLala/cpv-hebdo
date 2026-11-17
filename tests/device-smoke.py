"""Install and exercise the real startup/PIN flow on an Android 13 emulator."""
import re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path

PKG='mg.rmlala.cpv.debug'
def adb(*args):
    return subprocess.check_output(['adb',*args],text=True,timeout=30)
def tree():
    adb('shell','uiautomator','dump','/sdcard/cpv-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/cpv-ui.xml'))
def wait_node(predicate,timeout=60):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for node in tree().iter('node'):
            if predicate(node):return node
        time.sleep(1)
    print(ET.tostring(tree(),encoding='unicode'),flush=True)
    print(adb('logcat','-d','-s','AndroidRuntime:E'),flush=True)
    raise AssertionError('Expected Android UI did not appear')
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def enter_pin():
    tap(wait_node(lambda n:n.get('class')=='android.widget.EditText'))
    adb('shell','input','text','7391')
    hide_keyboard()
    tap(wait_node(lambda n:n.get('text','').casefold()=='continuer'))
def hide_keyboard():
    if re.search(r'mInputShown=true|mIsInputViewShown=true',adb('shell','dumpsys','input_method')):
        adb('shell','input','keyevent','4')

apk=next(Path('app/build/outputs/apk/api29/debug').glob('*.apk'))
adb('install','-r',str(apk));adb('logcat','-c')
adb('shell','am','start','-W','-n',PKG+'/mg.rmlala.cpv.MainActivity')
enter_pin();enter_pin()
wait_node(lambda n:'Votre semaine' in n.get('text',''),90)
adb('shell','am','force-stop',PKG)
adb('shell','am','start','-W','-n',PKG+'/mg.rmlala.cpv.MainActivity')
tap(wait_node(lambda n:n.get('class')=='android.widget.EditText'))
adb('shell','input','text','0000');hide_keyboard();tap(wait_node(lambda n:n.get('text','').casefold()=='déverrouiller'))
wait_node(lambda n:n.get('text')=='PIN incorrect.')
tap(wait_node(lambda n:n.get('class')=='android.widget.EditText'))
adb('shell','input','text','7391');hide_keyboard();tap(wait_node(lambda n:n.get('text','').casefold()=='déverrouiller'))
wait_node(lambda n:'Votre semaine' in n.get('text',''),90)
log=adb('logcat','-d','-s','AndroidRuntime:E')
assert 'FATAL EXCEPTION' not in log,log
print('Android 13: installed, PIN setup, encrypted WebView startup, restart and wrong/correct PIN passed.')

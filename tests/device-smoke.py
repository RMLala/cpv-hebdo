"""Real Android startup and optional PIN. Avoid accessibility dumps of the heavy WebView."""
import re,subprocess,time,xml.etree.ElementTree as ET,json,urllib.request
from pathlib import Path
PKG='mg.rmlala.cpv.debug'
def adb(*args):return subprocess.check_output(['adb',*args],text=True,timeout=30)
def tree():
    adb('shell','uiautomator','dump','/sdcard/cpv-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/cpv-ui.xml'))
def wait_node(predicate,timeout=60):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        try:
            for node in tree().iter('node'):
                if predicate(node):return node
        except (subprocess.SubprocessError,ET.ParseError):pass
        time.sleep(1)
    print(adb('logcat','-d','-s','AndroidRuntime:E'),flush=True)
    raise AssertionError('Expected native Android UI did not appear')
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def button(text):tap(wait_node(lambda n:n.get('text','').casefold()==text.casefold()))
def hide_keyboard():
    if re.search(r'mInputShown=true|mIsInputViewShown=true',adb('shell','dumpsys','input_method')):adb('shell','input','keyevent','4')
def enter_pin(value,submit):
    tap(wait_node(lambda n:n.get('class')=='android.widget.EditText'));adb('shell','input','text',value);hide_keyboard();button(submit)
def cdp(expression):
    pid=adb('shell','pidof',PKG).strip().split()[0]
    adb('forward','tcp:9222','localabstract:webview_devtools_remote_'+pid)
    with urllib.request.urlopen('http://127.0.0.1:9222/json',timeout=10) as r:pages=json.load(r)
    target=next(p for p in pages if '/assets/index.html' in p.get('url',''))
    return json.loads(subprocess.check_output(['node','tests/device-web.cjs',target['webSocketDebuggerUrl'],expression],text=True,timeout=35))
def wait_web():
    end=time.monotonic()+100
    while time.monotonic()<end:
        try:
            if cdp("typeof bootComplete3!=='undefined' && bootComplete3 && !document.querySelector('main').inert"):return
        except (Exception,):pass
        time.sleep(2)
    raise AssertionError('WebView startup failed')
def restart():
    adb('shell','am','force-stop',PKG);adb('shell','am','start','-W','-n',PKG+'/mg.rmlala.cpv.MainActivity')
apk=next(Path('app/build/outputs/apk/api29/debug').glob('*.apk'))
adb('install','-r',str(apk));adb('shell','pm','clear',PKG);adb('logcat','-c');restart();wait_web()
assert cdp("JSON.parse(CpvNative.securityStatus()).enabled")==False
cdp("state.draft.page='PIN preservation fixture';dbWrite(structuredClone(state)).then(()=>true)")
cdp("CpvNative.securityAction('enable');true");enter_pin('7391','Continuer');enter_pin('7391','Continuer');wait_web()
assert cdp("JSON.parse(CpvNative.securityStatus()).enabled")==True
restart();enter_pin('0000','Déverrouiller');wait_node(lambda n:n.get('text')=='PIN incorrect.');enter_pin('7391','Déverrouiller');wait_web()
assert cdp("state.draft.page")=='PIN preservation fixture'
cdp("CpvNative.securityAction('disable');true");button('Désactiver');enter_pin('7391','Déverrouiller');wait_web();restart();wait_web()
assert cdp("JSON.parse(CpvNative.securityStatus()).enabled")==False
assert cdp("state.draft.page")=='PIN preservation fixture'
assert cdp("document.getElementById('pinEnabled3')!==null && document.getElementById('biometricEnabled3')!==null")==True
log=adb('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in log,log
print('Android 13: direct startup, optional PIN setup, wrong/correct PIN, disabling and data preservation passed.')

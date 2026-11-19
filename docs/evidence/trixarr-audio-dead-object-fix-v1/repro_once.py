"""One bounded startup attempt, or classify an existing sanitized trace. No retries/restarts."""
from pathlib import Path
import argparse, subprocess, re, time, xml.etree.ElementTree as E
ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--trace', type=Path, help='Classify captured output without touching the device')
args = parser.parse_args()

def verdict(lines):
    fatal = any(re.search(r'code=-6 recoverable=false .*positionMs=0\b', x) for x in lines)
    terminal = any('Got error from player' in x for x in lines)
    if fatal and terminal:
        print('RED: DEAD_OBJECT -6 before playback; terminal player failure')
        return 2
    if any('Play method:' in x for x in lines):
        print('GREEN: playback reached onPrepared; terminal startup failure not reproduced')
        return 0
    print('INCONCLUSIVE: no playing or exact failure signal in bounded sample')
    return 3

if args.trace:
    raise SystemExit(verdict(args.trace.read_text().splitlines()))
adb = ['adb', '-s', '100.91.172.18:5555']
pkg = 'se.viktorjonsson.jellyfintv.debug'
def call(*parts):
    return subprocess.check_output(adb + list(parts), text=True, timeout=18).strip()
pid = call('shell', 'pidof', pkg)
assert pid.isdigit(), 'Existing app process required; this harness never starts/restarts the app process'
subprocess.run(adb + ['shell','am','start','-n',pkg+'/org.jellyfin.androidtv.ui.startup.StartupActivity',
    '--es','ItemId','dae676fc-22d2-a272-784e-30b9ff521ab8'], check=True, capture_output=True, timeout=15)
call('shell', 'uiautomator', 'dump', '/sdcard/dead-object-repro-ui.xml')
root = E.fromstring(call('shell', 'cat', '/sdcard/dead-object-repro-ui.xml'))
nodes = list(root.iter('node'))
assert any(x.get('text') == 'Resident Evil' for x in nodes), 'Expected movie details screen'
buttons = [x for x in nodes if x.get('package') == pkg and (x.get('text') or x.get('content-desc')) in ['Spela upp','Återuppta','Play','Resume']]
assert buttons, 'No supported play button found; no click performed'
box = list(map(int, re.findall(r'\d+', buttons[0].get('bounds', ''))))
assert len(box) == 4 and box[3] > box[1] >= 0
start = call('shell','date','+%m-%d %H:%M:%S')
call('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2))
time.sleep(7)
assert call('shell','pidof',pkg) == pid, 'App process changed'
raw = call('shell',f'logcat -d -v threadtime --pid={pid} -T "{start}.000" PlaybackDiagnostics:V PlaybackController:V VideoManager:V AudioTrack:V DefaultAudioSink:V ExoPlayerImpl:V "*:S"')
lines = [x.rstrip() for x in raw.splitlines() if not re.search(r'https?://|token|authorization|cookie|password|api.?key|path|url|/storage/|/mnt/',x,re.I)]
(ROOT/'latest-manual-startup-signal.txt').write_text('\n'.join(lines)+'\n')
raise SystemExit(verdict(lines))

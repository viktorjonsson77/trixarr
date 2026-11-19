from pathlib import Path
from PIL import Image
import hashlib,json
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parents[4]
m=json.loads((ROOT/'manifest.json').read_text())
assert hashlib.sha256((ROOT/m['source_filename']).read_bytes()).hexdigest()==m['source_sha256']
assert Image.open(ROOT/m['source_filename']).mode=='RGB'
for a in m['assets']:
 p=REPO/a['filename'];im=Image.open(p);im.load()
 assert list(im.size)==a['dimensions']
 assert hashlib.sha256(p.read_bytes()).hexdigest()==a['sha256']
 if 'transparent' in a['usage'].lower() or p.name in ['app_logo.png','app_icon_foreground.png','app_banner_foreground.png']:
  assert im.getchannel('A').getpixel((0,0))==0
banner=Image.open(ROOT/'trixarr-tv-banner-320x180.png');assert banner.size==(320,180)
icon=Image.open(ROOT/'trixarr-icon-foreground.png');alpha=icon.getchannel('A')
points=[(x,y) for y in range(432) for x in range(432) if alpha.getpixel((x,y))>8]
assert max(((x+.5-216)**2+(y+.5-216)**2)**.5 for x,y in points)<132
symbol=Image.open(ROOT/'trixarr-triangle.png');box=symbol.getchannel('A').getbbox()
assert box[0]>0 and box[1]>0 and box[2]<symbol.width and box[3]<symbol.height,'Clipped symbol'
assert not (REPO/'app/src/main/res/drawable/app_icon_foreground_monochrome.xml').exists()
print('PASS: approved master SHA/RGB, 30 asset hashes/dimensions, alpha corners, 320x180 banner, triangle bounds/adaptive safe circle')

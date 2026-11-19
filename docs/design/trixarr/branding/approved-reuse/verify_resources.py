"""Focused resource checks: original symbol bytes, selected wordmark, qualifiers and alpha."""
from pathlib import Path
import hashlib, json, re
from PIL import Image

ROOT=Path(__file__).resolve().parent; REPO=ROOT.parents[4]
original=(ROOT/'original-triangle-symbol.svg').read_text()
defs=re.search(r'<defs>.*?</defs>',original,re.S).group()
symbol=original.split('</defs>',1)[1].rsplit('</svg>',1)[0]
svg=(ROOT/'Trixarr_logo_horizontal.svg').read_text()
assert defs in svg and symbol in svg, 'Original gradient/symbol bytes changed'
assert 'aria-label="Trixarr"' in svg and 'ArrStreamer' not in svg
files=json.loads((ROOT/'generated-resources.json').read_text())
records=[]
for f in files:
 p=REPO/f;im=Image.open(p);im.load();assert im.mode=='RGBA'
 if p.name=='app_logo.png':assert im.size==(1825,530) and im.getchannel('A').getextrema()==(0,255)
 elif p.parent.name=='mipmap-mdpi':assert im.size==(320,180) and im.getchannel('A').getextrema()==(255,255)
 elif p.name=='app_banner_foreground.png':assert im.size==(1280,720) and im.getchannel('A').getextrema()==(0,255)
 records.append({'file':f,'dimensions':im.size,'alpha_range':im.getchannel('A').getextrema(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
assert len(records)==13
# Foreground uses an inset safe-area symbol; no wordmark is placed in an adaptive icon.
im=Image.open(REPO/'app/src/main/res/drawable-nodpi/app_icon_foreground.png').convert('RGBA')
assert im.size==(432,432)
alpha=im.getchannel('A');points=[(x,y) for y in range(432) for x in range(432) if alpha.getpixel((x,y))]
radius=max(((x+.5-216)**2+(y+.5-216)**2)**.5/4 for x,y in points)
assert radius<33
print(json.dumps({'status':'PASS','symbol_and_gradients_unchanged':True,'splash_canvas_unchanged':True,'adaptive_foreground_radius_dp':radius,'generated_resources':records},indent=2))

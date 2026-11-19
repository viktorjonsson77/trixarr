"""Deterministic raster derivatives; no redrawing, recoloring or AI generation."""
from pathlib import Path
from PIL import Image, ImageChops, ImageOps
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parents[4]
SOURCE=ROOT/'trixarr-approved-logo.png'
EXPECTED='22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==EXPECTED
im=Image.open(SOURCE).convert('RGBA')
assert im.size==(2172,724)
r,g,b,a=im.split()
# Background-only samples have max RGB <=28. Preserve source RGB and feather
# alpha through 32..64; this is a raster matte, not recovery of original vectors.
strength=ImageChops.lighter(ImageChops.lighter(r,g),b)
alpha=strength.point(lambda v: max(0,min(255,round((v-32)*255/32))))
transparent=im.copy();transparent.putalpha(alpha)
symbol=transparent.crop((225,150,610,580))
BG=(1,2,17,255)
records=[]
def save(p,image,usage):
 p.parent.mkdir(parents=True,exist_ok=True);image.save(p)
 records.append({'filename':str(p.relative_to(REPO)),'format':'PNG','dimensions':list(image.size),'alpha_range':list(image.getchannel('A').getextrema()),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'usage':usage})
def center(image,size,limit,bg=(0,0,0,0)):
 canvas=Image.new('RGBA',size,bg);scaled=ImageOps.contain(image,limit,Image.Resampling.LANCZOS)
 canvas.alpha_composite(scaled,((size[0]-scaled.width)//2,(size[1]-scaled.height)//2));return canvas
save(ROOT/'trixarr-logo-transparent.png',transparent,'Full canvas transparent matte; original proportions/RGB retained')
save(ROOT/'trixarr-triangle.png',symbol,'Triangle-only transparent source crop; no symbol clipping')
icon=center(symbol,(432,432),(180,180))
save(ROOT/'trixarr-icon-foreground.png',icon,'Adaptive icon foreground within safe circle')
banner=center(transparent,(320,180),(304,150),BG)
save(ROOT/'trixarr-tv-banner-320x180.png',banner,'Android TV banner')
save(ROOT/'trixarr-splash.png',transparent,'Splash artwork; FIT_CENTER preserves proportions')
save(REPO/'app/src/main/res/drawable-nodpi/app_logo.png',transparent,'Splash ImageView')
save(REPO/'app/src/main/res/drawable-nodpi/app_icon_foreground.png',icon,'Adaptive launcher icon')
mono=Image.new('RGBA',icon.size,(255,255,255,0));mono.putalpha(icon.getchannel('A'))
save(REPO/'app/src/main/res/drawable-nodpi/app_icon_foreground_monochrome.png',mono,'Android themed icon alpha silhouette from approved triangle')
old=REPO/'app/src/main/res/drawable/app_icon_foreground_monochrome.xml'
if old.exists():old.unlink()
for flavor in ['main','debug']:
 res=REPO/f'app/src/{flavor}/res'
 save(res/'drawable-nodpi/app_banner_foreground.png',center(transparent,(1280,720),(1216,600)),'TV banner foreground')
 for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
  save(res/f'mipmap-{density}/app_banner.png',center(transparent,(int(320*scale),int(180*scale)),(int(304*scale),int(150*scale)),BG),'TV launcher banner')
  n=int(48*scale);save(res/f'mipmap-{density}/app_icon.png',center(symbol,(n,n),(int(n*.70),int(n*.70)),BG),'Legacy launcher triangle-only icon')
 for name in ['app_icon_background.xml','app_banner_background.xml']:
  p=res/'drawable'/name
  if p.exists():
   import re
   p.write_text(re.sub(r'#[0-9a-fA-F]{6}', '#010211',p.read_text()))
manifest={'source_filename':SOURCE.name,'source_sha256':EXPECTED,'source_dimensions':[2172,724],'source_format':'RGB PNG; opaque, no alpha','master_unchanged':True,'method':'Source RGB preserved; max-RGB alpha matte 32..64; triangle crop [225,150,610,580]; LANCZOS aspect-fit, no stretching; navy canvas #010211','limitations':'Transparency is derived from flattened raster, not original designer alpha. Low-valued edge pixels are feathered. Master remains byte-identical. Debug/release use identical approved artwork.','assets':records}
(ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Generated',len(records),'assets; master verified')

"""User-authorized text-only rebrand; preserve the original SVG symbol and font metrics."""
from pathlib import Path
import hashlib, io, json, re, struct, zlib, subprocess
import cairosvg
from PIL import Image
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[4]
FONT = Path('/usr/share/fonts/truetype/lato/Lato-Black.ttf')
assert hashlib.sha256(FONT.read_bytes()).hexdigest() == '4ed66fa3928ae769205635c916b4f9e6a63f6a77f5d9693ac31a83d0d96ae1ef'
original = (ROOT/'original-triangle-symbol.svg').read_text()
assert hashlib.sha256(original.encode()).hexdigest() == '9c98d59fc4d71fe1b64d0d37c397a04564497aef437b315ae37e46e5da552bc2'
DEFS = re.search(r'<defs>.*?</defs>', original, re.S).group()
SYMBOL = original.split('</defs>', 1)[1].rsplit('</svg>', 1)[0]
font = TTFont(FONT); glyphs = font.getGlyphSet(); cmap = font.getBestCmap()

def word(text):
    cursor=0; paths=[]; bounds=[]
    for char in text:
        name=cmap[ord(char)]; pen=SVGPathPen(glyphs); glyphs[name].draw(pen)
        bp=BoundsPen(glyphs); glyphs[name].draw(bp); x0,y0,x1,y1=bp.bounds
        bounds.append((cursor+x0,y0,cursor+x1,y1))
        paths.append(f'<path transform="translate({cursor} 0)" d="{pen.getCommands()}"/>')
        cursor += font['hmtx'][name][0]
    return paths, (min(b[0] for b in bounds),min(b[1] for b in bounds),max(b[2] for b in bounds),max(b[3] for b in bounds))

_, old = word('ArrStreamer')
paths, (xmin,ymin,xmax,ymax) = word('Trixarr')
scale=1300/(old[2]-old[0])  # Same font size as the original, not widened to fill its old word length.
WORD=f'<g id="wordmark" fill="#ffffff" aria-label="Trixarr"><g transform="translate({615-xmin*scale:.6f} {496+old[1]*scale:.6f}) scale({scale:.9f} {-scale:.9f})">'+''.join(paths)+'</g></g>'
right=615+(xmax-xmin)*scale

def svg(body, width, height, view=None):
    return f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" width="{width}" height="{height}" viewBox="{view or f"0 0 {width} {height}"}"><title>Trixarr</title>{DEFS}{body}</svg>'

def render(data,w=None,h=None):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=data.encode(),output_width=w,output_height=h))).convert('RGBA')

changed=[]
def save(path,image):
    path.parent.mkdir(parents=True,exist_ok=True)
    image.save(path)
    changed.append(str(path.relative_to(REPO)))

def main():
    dx=100+1825/2-(114+right)/2
    logo=svg(f'<g transform="translate({dx:.9f} 0)">{SYMBOL}{WORD}</g>',1825,530,'100 125 1825 530')
    (ROOT/'Trixarr_logo_horizontal.svg').write_text(logo+'\n')
    save(REPO/'app/src/main/res/drawable-nodpi/app_logo.png',render(logo))
    for debug in [False,True]:
        res=REPO/('app/src/debug/res' if debug else 'app/src/main/res')
        s=.1556; x=(320-(right-114)*s)/2-114*s
        logo_body=f'<g transform="translate({x:.9f} 29.4) scale({s})">{SYMBOL}{WORD}</g>'
        badge='<rect x="247" y="145" width="58" height="21" rx="5" fill="#4934a6"/><text x="276" y="160" text-anchor="middle" font-family="Lato" font-weight="900" font-size="12" fill="#ffffff">DEBUG</text>' if debug else ''
        bg='<rect width="320" height="180" fill="#090d20"/>'
        banner=svg(bg+logo_body+badge,320,180)
        (ROOT/('Trixarr_banner_debug.svg' if debug else 'Trixarr_banner.svg')).write_text(banner+'\n')
        save(res/'drawable-nodpi/app_banner_foreground.png',render(svg(logo_body+badge,320,180),1280,720))
        for density,factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
            save(res/f'mipmap-{density}/app_banner.png',render(banner,int(320*factor),int(180*factor)))
    (ROOT/'generated-resources.json').write_text(json.dumps(changed,indent=2)+'\n')
    # Canonical 320x180 preview is the mdpi resource, not an independently redesigned image.
    print(json.dumps({'symbol_and_gradients_sha256':hashlib.sha256((DEFS+SYMBOL).encode()).hexdigest(),'font_scale_unchanged':scale,'logo_dimensions':[1825,530],'banner_dimensions':[320,180],'generated_resources':changed},indent=2))

if __name__=='__main__': main()

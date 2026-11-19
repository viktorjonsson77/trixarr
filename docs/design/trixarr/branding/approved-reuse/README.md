# Approved triangle reuse — Trixarr

The user explicitly instructed: **“Du kan använda den gamla triangeldesignen och bara byta texten.”** This authorizes the existing blue/purple ArrStreamer triangle and only a wordmark change to Trixarr. It supersedes use of the truncated triangle-v1 GitHub PNGs. The earlier cursive GitHub designs are not substituted.

`original-triangle-symbol.svg` is a byte-for-byte preserved copy of `docs/design/arrstreamer/ArrStreamer_symbol.svg`, SHA-256 `9c98d59fc4d71fe1b64d0d37c397a04564497aef437b315ae37e46e5da552bc2`. Original gradients, Bézier paths and opacities are embedded unchanged in the derived SVGs. Original design/reference files are not modified.

`export_android.py` changes the wordmark to **Trixarr**, using the same Lato Black glyph metrics, original font scale, white fill and baseline. The shorter composition is centered without stretching or changing symbol size. The original transparent 1825×530 splash canvas and current ImageView/animation remain unchanged. TV banners fit the logo inside320×180 with the original background and debug badge. Existing symbol-only launcher/adaptive/monochrome icons are retained, not redesigned.

Host tools: CairoSVG, fontTools, Pillow. The script requires the exact original system Lato Black font checksum and preserves its SIL OFL notice in `Lato-LICENSE.txt`. No font or host renderer is added to Android dependencies.

```sh
python export_android.py
python verify_resources.py
```

`generated-resources.json` enumerates output paths. Verification checks exact SVG symbol/gradient bytes, the new wordmark, PNG integrity/alpha/dimensions and adaptive safe area. See `docs/evidence/trixarr-branding-v2/ACCEPTANCE.md` for actual tool versions, GitHub source inventory and device results.

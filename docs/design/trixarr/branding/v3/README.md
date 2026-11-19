# Trixarr approved raster branding V3

User-supplied source: `/tmp/trixarr-approved-logo.png`, copied unchanged as
`trixarr-approved-logo.png` (full-resolution 2172×724 RGB PNG).
Approved SHA-256: `22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`.

This is a flattened opaque raster, not a transparent/vector master. The master
retains its original dark navy background, colors, letter spacing and proportions.
No generated, redrawn, recolored or replacement logo was used. Previous approved
branding remains archived in its existing locations.

`derive_assets.py` preserves the source RGB values and derives a transparent alpha
matte from maximum channel intensity (background samples <=28; threshold32,
fully opaque64). Antialiased edges are feathered; this is not the designer's original
alpha and cannot perfectly reconstruct pre-flattened edge colors. The approved
opaque master remains authoritative. Triangle crop [225,150,610,580] includes edge
padding and excludes the wordmark. Aspect-fit LANCZOS scaling preserves proportions.
Navy output canvases use #010211. Android adaptive foreground stays inside the
66dp safe circle. The monochrome variant uses the same derived triangle alpha.

`manifest.json` records every derived filename, dimensions, SHA-256, alpha range
and intended Android use. Debug/release resources use identical approved artwork.
The 320×180 TV banner and full-size transparent splash show the complete logo;
launcher icons use only the complete triangle. No rectangular matte is composited
behind foreground resources. The existing splash FIT_CENTER view/animation remains.

Reproduce with Pillow:

```
python derive_assets.py
python verify_assets.py
```

The resource verifier checks original SHA/RGB, all derived hashes/dimensions,
transparency, symbol bounds, adaptive safe area and the TV banner dimensions.

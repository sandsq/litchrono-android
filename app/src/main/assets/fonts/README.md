# Bundled fonts

These fonts are bundled locally; no font provider, network access, or runtime
font download is required. All eight TTF files are unmodified upstream **static**
builds, not variable fonts or locally generated instances.

## Android resources

| Family resource | Regular (400, normal) | Bold (700, normal) | Italic (400, italic) | Bold italic (700, italic) |
| --- | --- | --- | --- | --- |
| `@font/open_sans` | `open_sans_regular.ttf` | `open_sans_bold.ttf` | `open_sans_italic.ttf` | `open_sans_bold_italic.ttf` |
| `@font/lora` | `lora_regular.ttf` | `lora_bold.ttf` | `lora_italic.ttf` | `lora_bold_italic.ttf` |

The family XML and TTF files live in `app/src/main/res/font/`. The XML includes
both framework (`android:`) and AndroidX-compatible (`app:`) attributes. On API
24–25, load through AndroidX `ResourcesCompat.getFont` or an AppCompat font-family
consumer; framework-only font resource loading requires API 26.

## Using the fonts

In app settings, tap the font field and choose **Open Sans** or **Lora**, then
save. The font applies to the app's clock and quote, and to the home-screen
widget's quote and attribution. The existing default (`sans-serif`) is unchanged.
Names are case-insensitive for bundled families; `open-sans`, `open_sans`, and
`opensans` also select Open Sans. Android system family names still work.

`AppFonts` loads the same family for both screens. Since custom typefaces cannot
be sent through `RemoteViews`, widget text is drawn locally into transparent,
memory-bounded bitmaps with screen-reader descriptions. HTML styling and author
search/paging actions are retained. Separate renders handle launcher sizes and
legacy portrait/landscape layouts.

Georgia is not included: it requires a separate embedding/distribution license.
Lora is a freely redistributable serif alternative, not a copy of Georgia.

Font loading, styles, bitmap limits, and `RemoteViews` serialization are covered
by `FontRenderingTest` in `app/src/androidTest/`. Run on a connected device with
`./gradlew :app:connectedDebugAndroidTest`.

## Provenance and licenses

### Open Sans

- Official repository: https://github.com/googlefonts/opensans
- Pinned commit: `6cbc400d4855abee59d85c4ed871d510399bce7b`
- Static source directory: https://github.com/googlefonts/opensans/tree/6cbc400d4855abee59d85c4ed871d510399bce7b/fonts/ttf
- Upstream files: `OpenSans-Regular.ttf`, `OpenSans-Bold.ttf`,
  `OpenSans-Italic.ttf`, `OpenSans-BoldItalic.ttf`.
- Bundled license: [`open_sans/OFL.txt`](open_sans/OFL.txt).
- License source: https://raw.githubusercontent.com/googlefonts/opensans/6cbc400d4855abee59d85c4ed871d510399bce7b/OFL.txt

### Lora

- Official repository: https://github.com/cyrealtype/Lora-Cyrillic
- Pinned commit: `2d53b449b60e185b39f671b44fded83e0910ad30`
- Static source directory: https://github.com/cyrealtype/Lora-Cyrillic/tree/2d53b449b60e185b39f671b44fded83e0910ad30/fonts/ttf
- Upstream files: `Lora-Regular.ttf`, `Lora-Bold.ttf`, `Lora-Italic.ttf`,
  `Lora-BoldItalic.ttf`.
- Bundled license: [`lora/OFL.txt`](lora/OFL.txt).
- License source: https://raw.githubusercontent.com/cyrealtype/Lora-Cyrillic/2d53b449b60e185b39f671b44fded83e0910ad30/OFL.txt

Both families are licensed under the SIL Open Font License, Version 1.1. Keep
the respective copyright notices and full licenses with redistributed fonts.
Only local filenames were changed to Android-compatible resource names; font
contents, internal names, glyph coverage, and hinting were not modified.

[`provenance.json`](provenance.json) records each bundled font/license path,
exact commit-pinned raw download URL, byte size, and SHA-256 checksum. Paths in
that manifest are relative to `app/src/main/`.

## Verification

All eight files were inspected with Fontconfig `fc-scan` and Python's standard
library SFNT table parsing. Checks confirmed:

- Family and PostScript names match Open Sans or Lora and the intended style.
- `OS/2.usWeightClass` is 400 for regular/italic and 700 for bold/bold italic.
- `OS/2.fsSelection` italic and `head.macStyle` bold/italic flags match filenames.
- No `fvar`, `gvar`, `avar`, `HVAR`, `MVAR`, or `VVAR` tables are present, and
  `fc-scan` reports `variable=False`.
- Both XML families map all four styles to the matching local fonts with
  consistent framework and AndroidX attributes.

To inspect a font locally, for example:

```sh
fc-scan app/src/main/res/font/open_sans_regular.ttf
```

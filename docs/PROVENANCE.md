# Primary-engine provenance and boundary

## The actual engine

Every barcode/QR detection, thresholding, decoding, format parsing, QR symbol
layout and QR error-correction operation is executed by **ZXing core 3.5.4**.
This is the complete unchanged upstream core, not a reimplementation or two
isolated helper routines. All 239 files under `core/src/main/` were copied
byte-for-byte, retaining their license headers. No legacy ZXing Barcode Scanner
Android app was forked; upstream explicitly says that old app does not work on
Android 14. The new Android shell targets API35 and uses only the core library.

- Upstream: https://github.com/zxing/zxing
- Annotated tag: `zxing-3.5.4`
- Tag object: `3116a1970d66973475352d8161f2c42bf0a0ea7f`
- Peeled commit: `f651b0a0375676e47144f73397dddff8868b0e4c`
- Source: https://github.com/zxing/zxing/tree/f651b0a0375676e47144f73397dddff8868b0e4c/core/src/main
- Archive: https://codeload.github.com/zxing/zxing/tar.gz/f651b0a0375676e47144f73397dddff8868b0e4c
- Archive SHA-256: `f41a8755d88a9955469459e40d27de2e3342cfc34e2fd7c268460f690d961ebb`
- License: Apache-2.0; upstream LICENSE, NOTICE, AUTHORS and source headers kept
- Byte ledger: `vendor/zxing/UPSTREAM.json`; `python3 tools/verify-upstream.py`
- Only added file inside upstream module aside from provenance: Gradle build adapter

The annotated tag resolves to the recorded commit through GitHub's tag API.
The original archive was compared to the vendored files. The ledger checks every
source member, so modified or extra source is rejected rather than hidden.

## Verified Telegram use, with the correct authorship

Telegram Android at `f2908b14133bbffbf7ab04f641ecb5bfaf533242` pins this exact version:

- [TMessagesProj/build.gradle, line 52](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/build.gradle#L52): `implementation 'com.google.zxing:core:3.5.4'`
- [CameraScanActivity.java, lines 1325–1344](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/src/main/java/org/telegram/ui/CameraScanActivity.java#L1325-L1344): bitmap pixels/YUV become ZXing luminance sources; `QRCodeReader.decode` performs the decode

ZXing is a mature **third-party dependency actually used by Telegram**, not
Telegram-authored code. This product depends on ZXing core directly. It neither
imports Telegram account flows nor calls Telegram APIs, servers or logins.
There is no GPL code copied from Telegram in this prototype.

## Functional ownership

| User flow | Mature upstream operation | Authored platform/shell boundary |
| --- | --- | --- |
| Choose image → decoded result | RGBLuminanceSource, HybridBinarizer, MultiFormatReader; all format-specific detectors/decoders | Android SAF chooser, bounded stream read, BitmapFactory sampling, bitmap pixels |
| Read vertical 1D barcode | Same complete ZXing decode pipeline | Android Matrix 90° fallback rotation after an unsuccessful first decode |
| Inspect payload type | ZXing ResultParser and format metadata | Plain selectable text, labels, never launch payload actions |
| Enter text → QR preview | QRCodeWriter, Encoder, mask/version/layout and Reed-Solomon error correction | UTF-8 length policy, UI, BitMatrix pixels copied into Android Bitmap |
| Save PNG | Same ZXing-produced symbol | Android PNG encoder and Storage Access Framework output stream |
| Copy/share | No algorithm needed | Android clipboard or explicit share chooser |
| Cancel, errors, appearance, licenses | No algorithm needed | Lifecycle tokens, worker cancellation, in-memory UI state and theme preference |

No custom QR/barcode algorithm, server, product database, history model or
networking implementation surrounds the engine. The only persistent app setting
is appearance. Current screen/draft state uses Android saved-instance state; it
is not a saved collection or history feature. User-requested PNGs persist at the
location selected in the system picker. Copying can be retained by the system
clipboard or the user's keyboard; the app explicitly tells the user this.

## Limits and deliberate omissions

- One recognized code per input image; multi-code discovery is not implemented
- Import maximum 20 MiB bytes and 64 million source pixels, sampled to ≤2,048 px
  on each side. Small/dense codes may be lost through sampling; a crop helps
- Generator capped at 1,200 UTF-8 bytes, 1,024 px PNG, M correction, 4-module margin
- Image EXIF is not applied; 90° fallback supports sideways 1D images, but
  mirrored or badly damaged inputs are not guaranteed
- Camera scanning, history, database, accounts, ads, payments, background jobs
  and URL auto-opening are intentionally absent from this bounded prototype
- English-only shell; real-device, current-Android and full TalkBack validation
  remain release work unless explicitly listed as passed in the QA report

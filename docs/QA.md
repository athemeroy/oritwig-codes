# Verification and limitations

Verified on 2026-10-01. This is an independently buildable prototype, not a
production-signed or store-published release.

## Passed

- Clean JDK21 / Gradle8.11.1 / AGP8.9.3 / SDK35 build, with all 76 tasks executed
- All 239 vendored ZXing source files byte-identical to the pinned upstream
- All 561 upstream tests across 132 concrete test classes, including image corpus
- All 16 local adapter tests and all 22 native Android checks
- Android lint with zero errors and zero warnings
- Native runner: `passed=22`, `INSTRUMENTATION_CODE: -1`, on an API26 x86 software emulator
- Actual system-picker flow: enter text → QR → save PNG → select exported PNG → inspect exact decoded payload
- Exported PNG independently checked: 1024×1024, exact expected QR pixels/text, zero EXIF
- Activity recreation, draft/reader restoration, dark system-bar contrast,
  malformed/oversized input, stale-export invalidation, vertical Code128 and
  absent internet/camera permissions

The final tested debug APK SHA-256 is
`d44be7016b7aae370c421314ff7d274297129409e8e25d989ddf5b4d84621689`.
A fresh local build may have a different debug-signing certificate or packaging hash.

The synthetic exported URL was `https://example.org/oritwig-codes`. Its 7,557-byte
PNG SHA-256 is
`1668cf15414af0089f39a7eb15d36ad910db60309ea4220d0e2351ffdfeb28b4`.
The app displayed it as QR CODE / URI / 33 characters without opening it.

## Not claimed as passed

API35 runtime, physical devices, full TalkBack, large-font/landscape sweeps,
every barcode format or photograph, and every provider-specific interruption.
Live camera is not implemented. A failed external write may leave a partial file;
its error message explains this. Image EXIF orientation is not applied.

The software emulator initially showed system ANR dialogs, dismissed before the
usable UI pass. No Codes app crash was observed. This is not physical-phone evidence.

## Reproduce

The README gives the independent build and native-runner commands. Unit tests
cover Unicode/plain/max-size QR roundtrips, limits, classification, inversion,
rotation, Code128, EAN13 and correction metadata. Native tests exercise Android
bitmap/stream/PNG boundaries, UI state and lifecycle behavior.

For the full upstream suite, use the exact archive and checksum in PROVENANCE.md.
Compile its `core/src/test/java` against the built ZXing JAR, JUnit4.13.2 and
Hamcrest1.3; run JUnitCore for all non-abstract `*TestCase` and `*Test` classes from
the extracted `core` directory with `src/test/resources` on the classpath.

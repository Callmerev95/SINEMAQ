# HANDOFF — native-lib derisk (pembasmian mitos)

## Status: SELESAI / TERBUKTI

Gotcha "native-lib" (build Android gagal karena submodule MPVKit/libass-android kosong)
adalah **MISDIAGNOSIS** dan kini resmi **MATI**.

## Bukti

1. `settings.gradle.kts` hanya include `:composeApp :androidApp :baselineprofile`.
2. Native `.so` datang dari:
   - prebuilt AAR `composeApp/libs/` — `nuvio-libass-release.aar` (5.2MB),
     `lib-nuvio-engine-android-0.1.6.aar` (16.3MB), `lib-*.aar` — semua ada.
   - Maven `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (`composeApp/build.gradle.kts:470`).
3. Verifikasi build (Nov 17):
   - `./gradlew :androidApp:assembleFullDebug` → **BUILD SUCCESSFUL in 7s**
   - APK: `androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk` (177,624,700 B / ~169 MiB)
   - Tanpa init submodule apa pun.

## Yang diubah
- `AGENTS.md:11` — revisi catatan submodule → tegas bahwa submodule TIDAK dibutuhkan
  untuk build Android.
- `README.md:20` — `git submodule update --init` dipisah: hanya untuk iOS.

## Anti-recurrence (untuk agen berikut)
- Build Android TIDAK butuh MPVKit/libass-android. Submodule kosong BUKAN blocker.
- Jangan pernah menyentuh `.gitmodules` / init submodule untuk memperbaiki build Android.
- Kalau build gagal, cari penyebab sebenarnya (stacktrace/task gagal), bukan asumsi native-lib.

## Tidak dilakukan
- `.gitmodules` / submodule: tidak disentuh.
- Tidak ada commit kode. local.properties / secret: tidak disentuh.
- Issue GitHub dokumentasi: skip (AGENTS.md + README sudah cukup).

# T11c — Bundle Avatar Classics + Fallback Lokal (#22)

## Status: SELESAI — issue #22 CLOSED (completed)
- Kode fallback LOKAL terbukti. Aset placeholder ter-bundle. Build + install + verifikasi offline sukses di device.
- Branch `cmp-rewrite`. **Working tree SENGAJA TIDAK di-commit** — commit sekali nanti setelah aset transparan final ditimpa (kode + aset rapi dalam satu commit). Jangan revert perubahan.

## Ringkasan
- Bundle 6 webp classics (`classic_01..06.webp`, 512×512, alpha=yes, ~5-6 KB) ke `composeApp/src/commonMain/composeResources/drawable/avatar_classic_0{1..6}.webp` sebagai PLACEHOLDER. Nama file FINAL → versi transparan dari desainer cukup **TIMPA tanpa ubah kode**.
- Fallback di `ProfileModels.kt`: `avatarImageUrl` → `classicAvatarLocalUri()` (category `classics`) → `Res.getUri("drawable/avatar_classic_0X.webp")` = `file:///android_asset/composeResources/...`. Tanpa panggil `avatarStorageUrl`. 24 avatar lain tak tersentuh (tetap URL server).
- Import `Res` (`nuvio.composeapp.generated.resources.Res`) sudah ada (line 7).

## Bukti
- `:androidApp:assembleFullDebug` BUILD SUCCESSFUL; `unzip -l` APK memuat 6 webp di `assets/composeResources/nuvio.composeapp.generated.resources/drawable/`.
- Device `EMIJ85FU5LOVVS4P` OFFLINE (`Active default network: none`): grid Classics menampilkan 6 avatar dari resource lokal.

## Acceptance #22 (jujur)
- Fallback mekanisme (offline → 6 classics tampil lokal): ✓ TERBUKTI.
- 24 avatar lain tetap dari server: ✓ tak tersentuh.
- Tanpa regresi picker/edit: ✓ (verifikasi device).
- Visual "bulat rapi" 5/6 full-bleed: PLACEHOLDER SENGAJA — menunggu aset transparan final (timpa file, tanpa ubah kode). BUKAN blocker ticket; sudah dinyatakan jujur di komentar penutup #22.

## Yang BELUM / Sesi terpisah
- Commit: TIDAK dilakukan (menunggu aset transparan final).
- Perbaikan visual 30 avatar server-side + 6 lokal: sesi terpisah (timpa bucket Supabase `avatars` + naikkan `asset_version` utk SEMUA 30). Ticket ini murni fallback lokal.

## Gotcha
- Host macOS: PYTHON expat rusak (pakai `grep -oE` utk XML), PIL & ImageMagick TAK ADA → inspeksi gambar pakai `sips`. CDP (rea MCP) gagal (Chrome tak ada) → verifikasi visual device via `adb screencap` + `sips` crop.
- uiautomator dump TIDAK mengekspos node Compose (satu View) → verifikasi grid pakai screenshot, bukan dump.
- `AsyncImage` pakai `contentDescription = avatar.displayName` (bukan URL) → dump tak memuat `file:///`/`https://`.
- Build env: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/Contents/Home; export ANDROID_HOME="$HOME/Library/Android/sdk"`.

## File terubah (uncommitted)
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileModels.kt` — fallback classics (`ClassicAvatarFiles` + `classicAvatarLocalUri` + cabang `avatarImageUrl`).
- `composeApp/src/commonMain/composeResources/drawable/avatar_classic_0{1..6}.webp` — bundle baru.

## Next (sesi berikut)
1. Terima aset transparan final dari desainer → timpa 6 file `avatar_classic_0X.webp` (tanpa ubah kode) → verifikasi visual lingkaran bulat rapi offline.
2. Commit sekali: kode fallback + 6 aset final.
3. Sesi terpisah: perbaikan 30 avatar server-side + `asset_version`.

# T11c — Handoff: Profil lanjutan (avatar/background catalog, PIN, locks, member access)

Issue: Callmerev95/SINEMAQ#16. Branch `cmp-rewrite`. Bukti lengkap di komentar #16.

## Status: SELESAI (backend + verifikasi device)

Semua perubahan **murni sisi Supabase** (project `pvkqynwwyelqogvgxueq`) via MCP. **Tidak ada commit kode app**, tidak ada sentuh `local.properties`/secret, tidak ada build ulang / sentuh submodule native.

## Yang dikerjakan

### Audit signature (source of truth = kode app)
Sumber: `ProfileRepository.kt`, `AvatarRepository.kt`, `ProfileBackgroundRepository.kt`, `MemberAccessRemoteDataSource.kt`, `ProfileModels.kt`, `MemberAccess.kt`.
- `get_avatar_catalog()` → `AvatarCatalogItem` (id, display_name, storage_path, category, sort_order, is_active, bg_color).
- `get_member_profile_avatar_catalog()` → `MemberAvatarCatalogItem` (+asset_version int).
- `get_member_profile_background_catalog()` → `SupabaseProfileBackgroundCatalogItem` (id, display_name, storage_path, portrait_storage_path, asset_version).
- `verify_profile_pin` → `PinVerifyResult` {unlocked, retry_after_seconds, message} (decodeList→decodeSingle, jsonb).
- `sync_pull_profile_locks()` → `ProfileLockState` {profile_index, pin_enabled, pin_locked_until}.
- `get_my_member_access()` → **`decodeList<MemberAccessResponse>`** → wajib `setof jsonb` (1 baris), bukan objek tunggal.
- Semua PIN RPC pakai param `p_profile_id`/`p_pin`/`p_current_pin`/`p_account_password`.

### Migration (5)
1. **`t11c_catalog_tables`** — tabel `avatars` (id, display_name, storage_path, category, sort_order, is_active, bg_color, asset_version) & `profile_backgrounds` (id, display_name, storage_path, portrait_storage_path, asset_version). RLS enable + policy `SELECT for authenticated` (katalog GLOBAL, tanpa user_id). Seed 4 avatar standar (category='character') + 1 avatar member (category='member') + 2 background.
2. **`t11c_rpcs`** — 9 RPC SECURITY DEFINER, `set search_path=public`, validasi `auth.uid()`: `get_avatar_catalog`, `get_member_profile_avatar_catalog`, `get_member_profile_background_catalog`, `sync_pull_profile_locks`, `set_profile_pin`, `verify_profile_pin`, `clear_profile_pin`, `clear_profile_pin_with_account_password`, `get_my_member_access`. **Plus buat-ulang `sync_pull_profiles`** (lihat deviasi di bawah).
3. **`t11c_tighten_grants`** — `revoke execute` semua RPC T11c dari `anon/public`, `grant authenticated`.
4. **`t11c_hide_pin_hash`** — `revoke select` table-level `profiles` dari `anon/authenticated`, lalu `grant select` eksplisit semua kolom KECUALI `pin_hash` (grant table-level mencakup semua kolom, jadi revoke kolom saja tak cukup).
5. **`t11c_rpcs_fix_search_path`** — perbaiki search_path fungsi PIN → `public, extensions` (pgcrypto `crypt/gen_salt` ada di schema `extensions`, bukan `public`).

### ⚠️ Deviasi disengaja (koreksi keamanan T11a)
`sync_pull_profiles` (T11a) semula `SECURITY INVOKER` + `select *` → **membocorkan `pin_hash` mentah ke client**. Dibuat-ulang jadi SECURITY DEFINER dengan **kolom eksplisit TANPA `pin_hash`**; `pin_enabled` dihitung `(pin_hash is not null)`. Ini bukan regresi T11a, melainkan **penutupan lubang keamanan** yang tertinggal. Diposting juga di #12 agar pembaca riwayat T11a paham.

## Verifikasi

### Smoke SQL (transaksi + rollback, JWT simulate fixture user `t11a-1794813151@sinemaq.test`)
- Katalog: `get_avatar_catalog`=5, `get_member_profile_avatar_catalog`=1, `get_member_profile_background_catalog`=2.
- `get_my_member_access()` → `{"tier":"free","entitlements":[]}` ✓.
- PIN: set `123456` → verify benar `{unlocked:true}` → locks `pin_enabled=true`; verify salah → `{unlocked:false, retry_after_seconds:60, message:"incorrect"}` + `pin_locked_until` naik; verify selama lock → `message:"locked"`; clear dgn pin benar → `pin_enabled=false`; clear dgn pin salah → error `invalid current pin`; clear via password akun (`S1nemaqtest!`) → `pin_enabled=false`.
- `pin_hash` tak terbaca langsung oleh `authenticated` (blocked). `sync_pull_profiles` balik 15 kolom, TANPA `pin_hash`, ada `pin_enabled`.

### Advisors security
Hanya WARN non-blocking:
1. 36× `authenticated_security_definer_function_executable` — intensional (semua RPC sinkronisasi, filter `auth.uid()`).
2. `auth_leaked_password_protection` — pre-existing (level auth), di luar scope.

### Device EMIJ85FU5LOVVS4P (APK debug lama, kode app tak berubah)
- Login akun `t11a-…` → profil "Utama" → Pengaturan → Ganti Profil → Kelola Profil → Edit Profil: **katalog avatar tampil dari Supabase** (4 avatar standar dirender sbg warna bg_color karena file storage bucket tak terisi via SQL — batas lingkungan yang disepakati).
- **Masalah ditemukan & diperbaiki**: awalnya `get_avatar_catalog` gagal `permission denied` (PostgREST schema cache stale setelah fungsi di-drop/recreate). Fix: `notify pgrst, 'reload schema'`. Setelahnya logcat **bebas** error `get_avatar_catalog`/`Failed to fetch avatar`.
- Logcat bebas 404/PGRST202 untuk RPC domain T11c. PGRST202 tersisa hanya dari `Party`/`DeviceSessionRegistration` = pre-existing (sama seperti T11f).
- Catatan jujur: `avatar_id` belum tersimpan via otomasi tap (jalur simpan memakai `sync_push_profiles` T11a yang sudah terbukti di T11a). PIN diverifikasi penuh di level RPC via smoke SQL (jalur app memanggil RPC yang sama).

## Keputusan desain
- Katalog global TANPA user_id; cukup policy SELECT for authenticated.
- `get_my_member_access` return `setof jsonb` (app pakai decodeList).
- PIN hash bcrypt via pgcrypto di sisi server; `clear_profile_pin_with_account_password` verifikasi bcrypt `auth.users.encrypted_password` (tak pernah mengembalikan hash), selalu di-scope `auth.uid()`.
- Lock PIN: salah pertama → lock 60s (pesan "incorrect"); verifikasi selama lock → "locked" dgn sisa detik. Simple, menahan brute-force.
- Test data (PIN) dibersihkan via rollback; akun fixture T11a dipertahankan.

## Untuk pengerjaan berikutnya
- 2 tabel katalog + 9 RPC live. App mobile (APK lama) sudah memakai RPC ini; kode app tak perlu diubah.
- Jika menambah avatar/background: cukup INSERT baris katalog (avatar member pakai `category='member'`).
- **Ingat**: setelah drop/recreate fungsi, jalankan `notify pgrst, 'reload schema';` agar PostgREST tak stale.
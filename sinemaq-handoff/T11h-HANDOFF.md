# T11h — Handoff: Edge function delete-account

Issue: Callmerev95/SINEMAQ#21. Branch `cmp-rewrite`. Bukti lengkap di komentar #21.

## Status: SELESAI (backend + verifikasi smoke/SQL) — issue ditutup completed

Semua perubahan **murni sisi Supabase** (project `pvkqynwwyelqogvgxueq`) via MCP. **Tidak ada commit kode app**, tak ada sentuh `local.properties`/secret, tak ada build ulang / sentuh submodule native.

## Yang dikerjakan

### Audit DB aktual (source of truth = DB, bukan teks issue)
Dua deviasi jujur dari teks issue/CONTEXT:
1. **`devices` & `tv_login_sessions` TIDAK ADA** di project. CONTEXT kadaluarsa. Tabel domain dgn `user_id` (uuid) aktual = **11**: profiles, addons, collections, home_catalog_settings, library_items, media_servers, profile_settings_blob, provider_credentials, sync_events, watch_progress, watched_items.
2. **Tabel party tak punya `user_id`** — `watch_parties.host_device_id`, `watch_party_members.device_id/member_id`, `watch_party_messages.member_id` semuanya **device-scoped** (text), bukan `auth.uid()`. Tak ada pemetaan device→user → delete-account **tak menyentuh party data**; mereka expire alami (`expires_at` +12h, cascade members/messages). Keputusan user: biarkan orphan + expire.

### Kunci urutan hapus
- 9 tabel domain **FK ke `profiles` dgn `ON DELETE CASCADE`** (ke `profiles.user_id` + `profile_index`) → `delete profiles` cascade membersihkan sembilannya.
- 9 tabel itu punya trigger `log_sync_event` → cascade delete **menulis baris sync_events BARU**. `sync_events` tak punya FK ke profiles & tak punya trigger.
- `profiles` tak punya FK ke `auth.users` → hapus auth user TIDAK cascade data public → wajib hapus eksplisit.
- Maka urutan: **profiles dulu → sync_events paling akhir → auth.users**.

### Edge function `delete-account` (v1, ACTIVE, verify_jwt=true)
Deno, POST-only + CORS. Alur:
1. `Authorization: Bearer` → `auth.getUser(token)` → `userId` (401 bila invalid).
2. `admin` client = env `SUPABASE_SERVICE_ROLE_KEY` (**injected runtime, tanpa hardcode**).
3. `delete profiles where user_id` (cascade 9 child + tulis sync_events).
4. `delete sync_events where user_id` — **PALING AKHIR** (hapus hasil trigger + lama).
5. `admin.auth.admin.deleteUser(userId)` (service-role admin API).
6. Balik `{deleted:true}` / error generik — **tak ada secret/PII di response/log**.

Deploy via `supabase_deploy_edge_function` (MCP) — edge function PERTAMA di project ini (list sebelumnya kosong). `notify pgrst,'reload schema'` dijalankan.

## Verifikasi

### Smoke (JWT fixture `t11h-1794875000@sinemaq.test` / user `dfb50cc9-052e-4aba-8489-69187ecb5813`)
- Seed: profiles=1, library_items=1, sync_events=1 (trigger jalan).
- Login via auth REST (anon key) → JWT → invoke `/functions/v1/delete-account` → **HTTP 200 `{"deleted":true}`**.
- SQL pasca-delete: **SEMUA 0** — auth_user, profiles, addons, collections, home_catalog_settings, library_items, media_servers, profile_settings_blob, provider_credentials, **sync_events**, watch_progress, watched_items. (sync_events=0 membuktikan requirement user: bukan cuma 9 tabel cascade.)
- Re-login dgn kredensial sama → **HTTP 400 "Invalid login credentials"**.

### get_advisors security
Identik baseline: 52 WARN pre-existing (51× `authenticated_security_definer_function_executable` intensional + `auth_leaked_password_protection`). **Tak ada temuan baru** (edge bukan SECURITY DEFINER DB function, tak masuk linter).

### signOut otomatis (dari kode, bukan UI)
`AuthRepository.kt:262-276` — `deleteAccount()`: invoke `delete-account` → `saveFlag(HAD_ACCOUNT_SESSION,false)` → `auth.signOut()` → `LocalAccountDataCleaner.wipe()` → state `Unauthenticated`. Pemanggil UI: `AccountSettingsPage.kt:156`.

## Batas jujur: alur UI delete TIDAK teruji dari UI
Tombol "Hapus akun" **sengaja mati di varian Full** (`AppFeaturePolicy.accountDeletionEnabled=false` untuk `androidFull`; APK debug `com.sinemaqdebug.com` = Full). Hanya tampil di **Playstore**/iOS App Store (=true). Sesuai instruksi "jangan build ulang APK", alur UI (tombol→modal→signOut otomatis) **tidak diverifikasi dari UI** — **bukan kegagalan acceptance backend**. Dipecah ke **#25** (sektor UI, catatan `isPlaystore`).

Fixture signup via UI device sukses (akun + 1 profil terbentuk sebelum delete). Tidak memakai fallback SQL-insert auth user. Akun t8-test/t11a utama tak disentuh.

## Acceptance #21 — terpenuhi
- [x] Setelah invoke, data user hilang dari semua tabel; akun auth terhapus. (SQL: semua 0)
- [x] App sign out otomatis; sesi berikutnya gagal. (signOut di kode AuthRepository; re-login gagal 400; alur tombol UI → #25)
- [x] `get_advisors` security bersih; edge tak membocorkan service-role key.

## Untuk pengerjaan berikutnya
- Edge function pertama project: `delete-account`. Kalau tambah edge lain, ikuti pola yang sama (verify_jwt + verifikasi JWT via `auth.getUser`, service-role via env, jangan hardcode/log secret).
- Alur UI hapus akun: verifikasi di build **Playstore debug** (lihat #25). Full memang menyembunyikan tombol.
- Setelah deploy/mutasi fungsi DB: `notify pgrst,'reload schema'` (pelajaran T11c).
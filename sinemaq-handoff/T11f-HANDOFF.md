# T11f — Handoff: Sync lainnya (collections, addons, home_catalog, media_servers, provider_credentials, profile_settings_blob)

Issue: Callmerev95/SINEMAQ#19. Branch `cmp-rewrite`. Bukti lengkap di komentar #19.

## Status: SELESAI (backend + verifikasi device dua arah)

Semua perubahan **murni sisi Supabase** (project `pvkqynwwyelqogvgxueq`) via MCP. **Tidak ada commit kode app**, tidak ada sentuh `local.properties`/secret, tidak ada build ulang / sentuh submodule native.

## Yang dikerjakan

### Audit signature (source of truth = kode app)
Sumber: `CollectionSyncService.kt`, `AddonRepository.kt`, `HomeCatalogSettingsSyncService.kt`, `MediaServerSync.kt`, `ProviderCredentialSync.kt`, `ProfileSettingsSync.kt` + DTO.

Dua temuan kontrak yang diikuti:
1. **TIDAK ada cursor RPC** untuk domain T11f. `sync_get_*_delta_cursor` hanya dipakai adapter library/progress/watched. Ke-6 domain ini tak pernah memanggil delta cursor → **cursor RPC tidak dibuat** (hemat, sesuai gotcha).
2. **Semua push membawa `p_origin_client_id`** (`putSyncOriginClientId()`). Setiap RPC push menerima param ini (diabaikan) supaya tak 404/PGRST202.

### Kontrak row_key PER-DOMAIN
| Domain | row_key | Baris/(user,profile) |
|---|---|---|
| collections | `'collections'` (konstanta) | 1 |
| addons | `url` (per-baris) | multi |
| home_catalog_settings | `platform` (`'home_catalog_shared'`) | 1 |
| media_servers | `'media_servers'` (konstanta) | 1 |
| provider_credentials | `provider` (per-baris: tmdb/mdblist/debrid:*/animeskip/introdb) | multi |
| profile_settings_blob | `platform` (`'mobile'`) | 1 |

Single-row blob: row_key konstanta/`platform`. Multi-row: row_key = kolom pembeda (`url`/`provider`). Trigger `log_sync_event()` generic cukup baca 3 kolom kontrak.

### Migration (2, non-destruktif — tabel baru)
1. **`t11f_sync_tables`** — 6 tabel (`collections`,`addons`,`home_catalog_settings`,`media_servers`,`provider_credentials`,`profile_settings_blob`): kolom kontrak 3 (`user_id`,`profile_id`,`row_key` generated) + kolom blob + `updated_at`; unique constraint sesuai spesifikasi; FK → `auth.users` & `profiles(user_id,profile_index)`; index (`addons` by sort_order); RLS enable + policy `user_id=auth.uid()`; trigger `log_sync_event('<domain>')`.
2. **`t11f_rpcs`** — 11 RPC SECURITY DEFINER, `set search_path=public`, validasi `auth.uid()`, grant `authenticated`, revoke `anon/public`. `sync_push_addons` = full-state replace atomik (delete-then-upsert satu transaksi, `not exists` pada payload). Pull balik blob jsonb sesuai DTO app.

Catatan: header issue menulis "12 RPC" namun daftar enumerasi = 11 nama (5 pasang pull/push + `sync_push_addons` tunggal). Yang dibuat = 11, sesuai enumerasi.

## Verifikasi

### Smoke SQL (user fixture `t11a-1794813151@sinemaq.test`, profil 1)
push→pull→delete→pull per domain. Event I/U/D dinaikkan via trigger:
- collections / home_catalog_settings / media_servers / profile_settings_blob: pull balik blob ter-update; delete→pull kosong; **3 event (I/U/D)**.
- addons: full-state replace (A updated→A2/en=false, B dihapus) → **4 event (I,I,D,U)**.
- provider_credentials: 2 provider upsert per-provider → **3 event**.
- DB bersih pasca-smoke (`addons`=0, `sync_events` domain T11f=0).

### Device end-to-end (addon LIVE, dua arah) ✓
Device EMIJ85FU5LOVVS4P, APK debug lama (kode app tak berubah), akun `t11a-…`.
- **UI→DB** (`sync_push_addons`): pasang "Nuvio Catalog Addon" + "PencariMovie" dari UI → logcat `pushToServer() — success`, baris muncul di `addons`, trigger naikkan `sync_events`.
- **DB→UI** (REST select `pullFromServer`): hapus baris "PencariMovie" langsung di DB → restart app → layar Addon tampil **hanya 1 addon** (local storage masih punya URL-nya, tapi pull menimpa cache). Dua arah UI↔DB terbukti.
- Logcat bebas 404/PGRST202 untuk RPC domain T11f. (`PGRST202` dari `DeviceSessionRegistration`/`Party`/`AvatarRepository` = pre-existing, tak terkait.)

### Advisors security
6 tabel RLS aktif, policy 1 masing-masing. Sisa WARN non-blocking:
1. 26× `authenticated_security_definer_function_executable` (11 T11f + library/progress/watched) — intensional, semua filter `auth.uid()`.
2. `auth_leaked_password_protection` — pre-existing (level auth), di luar scope.

## Keputusan desain
- **Tanpa cursor RPC** untuk domain T11f (adapter tak memakai delta cursor) — menghindari RPC mati.
- **`p_origin_client_id` diterima semua push RPC** (diabaikan) demi kesesuaian kontrak app.
- **addons full-state replace atomik** — delete-then-upsert dalam satu transaksi (RPC = 1 txn) agar payload parsial tak kehilangan data.
- Test data (addon live + sync_events) **dibersihkan** pasca-verifikasi demi kebersihan test account.

## Untuk pengerjaan berikutnya
- 6 tabel + 11 RPC live. App mobile sudah sinkron (APK lama memuat adapter; kode app tak perlu diubah).
- Domain berikutnya (bila ada) tinggal mengikuti pola kontrak row_key per-domain + trigger `log_sync_event('<domain>')`.
- T11 selesai di sisi Supabase untuk seluruh 9 domain yang tercantum di check constraint `sync_events.domain`.

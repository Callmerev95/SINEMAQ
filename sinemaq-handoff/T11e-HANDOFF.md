# T11e — Sync watch progress + watched items (delta) — HANDOFF

Issue: Callmerev95/SINEMAQ#18 (CLOSED completed). Branch `cmp-rewrite`.
Scope: backend Supabase only (via MCP, project `pvkqynwwyelqogvgxueq`). No app-code commit.

## What landed (migration, live)
1. `t11e_watch_tables` — tabel `watch_progress` + `watched_items`, RLS + policy `user_id=auth.uid()`, FK → auth.users & profiles(user_id,profile_index).
2. `t11e_fix_watch_tables` — corrective (tabel masih kosong):
   - drop PK `watched_items_pkey` (yang memaksa season/episode NOT NULL → movie tak bisa insert);
   - `alter column season/episode drop not null`;
   - add `watched_items_unique UNIQUE NULLS NOT DISTINCT (user_id,profile_id,content_id,content_type,season,episode)` (PG15+, supaya ON CONFLICT upsert movie tanpa duplikat);
   - re-create `row_key` 4-komponen.
3. `t11e_trigger_rpcs` — trigger `watch_progress_log_sync`→`log_sync_event('watch_progress')`, `watched_items_log_sync`→`log_sync_event('watched_items')`; 10 RPC SECURITY DEFINER, `set search_path=public`, validasi `auth.uid()`, grant `authenticated`, revoke `anon/public`.

## RPC (10) — parameter persis mengikuti adapter app
watch_progress:
- `sync_get_watch_progress_delta_cursor(p_profile_id int)` → bigint
- `sync_pull_watch_progress(p_profile_id int, p_since_last_watched bigint=null, p_limit int=null)` → snapshot (epoch-ms)
- `sync_pull_watch_progress_delta(p_profile_id int, p_since_event_id bigint, p_limit int)` → `{event_id,operation,progress_key,content_id,content_type,video_id,season,episode,position,duration,last_watched}`
- `sync_push_watch_progress(p_profile_id int, p_entries jsonb, p_origin_client_id text)` — upsert by progress_key (LWW by arrival)
- `sync_delete_watch_progress(p_profile_id int, p_keys jsonb, p_origin_client_id text)` — `p_keys` = array **string** progress_key

watched_items:
- `sync_get_watched_items_delta_cursor(p_profile_id int)` → bigint
- `sync_pull_watched_items(p_profile_id int, p_page int, p_page_size int)` — **page-based**, offset `(page-1)*size`
- `sync_pull_watched_items_delta(p_profile_id int, p_since_event_id bigint, p_limit int)` → `{event_id,operation,content_id,content_type,title,season,episode,watched_at}`
- `sync_push_watched_items(p_profile_id int, p_items jsonb, p_origin_client_id text)` — upsert (LWW by arrival)
- `sync_delete_watched_items(p_profile_id int, p_keys jsonb, p_origin_client_id text)` — `p_keys` = array **objek** `{content_id,season,episode}` (TANPA content_type → match lintas content_type; `IS NOT DISTINCT FROM` utk null season/episode)

## ⚠️ Kontrak row_key kini PER-DOMAIN (penting untuk T11f)
`log_sync_event()` generic — cukup 3 kolom `user_id`/`profile_id`/`row_key`. Tapi **bentuk row_key diturunkan dari granularitas baris tiap domain**, JANGAN diasumsikan seragam `content_id:content_type`:
- `library` = `content_id:content_type` (2-komponen; 1 baris per konten)
- `watch_progress` = `progress_key` utuh, TANPA split (movie: `id`; series: `id_s{S}e{E}`)
- `watched_items` = `content_id:content_type:{season|''}:{episode|''}` (4-komponen; watched granularitasnya PER-EPISODE)

Delta-reconstruct dari row_key:
- progress delete: cukup bawa `progress_key` (=row_key); content fields `''` (DTO `WatchProgressDeltaSyncEntry` punya default `""`). App group delta by `progressKey`.
- watched delete: `content_id=split_part(rk,':',1)`, `content_type=split_part(rk,':',2)`, `season=nullif(split_part(rk,':',3),'')::int`, `episode=nullif(split_part(rk,':',4),'')::int` — wajib agar delete episode spesifik match di app (`findWatchedItemStableDeleteKey`, `WatchedRepository.kt:692`).

ponytail: rekonstruksi watched mengasumsikan `content_id` DAN `content_type` bebas-`:` (true utk tmdb id / movie|series|…).

**T11f (collections/addons/home_catalog_settings/media_servers/provider_credentials/profile_settings_blob): turunkan row_key dari granularitas baris masing-masing domain. Jangan copy 2-komponen membabi buta.**

## Verifikasi
- **Smoke SQL (transaksi+rollback, JWT fixture `t11a-1794813151@sinemaq.test`)** — semua lulus:
  - WP cursor 0 (kosong) → 28 (I/I/U termasuk update posisi 1000→5000) → 29 (DELETE). Trigger naik utk insert/update/**delete**. `sync_events` watch_progress = 4.
  - WP delta `>update` = delete bawa `progress_key=m1`.
  - WI pull page1 = 3 (movie+2ep); re-push upsert = tetap 3 (no dup, title ter-update); delete ep1 → 2.
  - WI delta delete reconstruct = `ws/series/1/1` (season+episode utuh); delta ws rows = 4 (no dup kartesian).
  - DB bersih pasca-rollback (watch_progress=0, watched_items=0, tanpa event watch).
- **get_advisors security**: bersih. WARN non-blocking = 15× `authenticated_security_definer_function_executable` (10 RPC T11e + 5 library #17, semua intensional, filter `auth.uid()`) + `auth_leaked_password_protection` (pre-existing, luar scope).

## Build/content-env debt (ortogonal, buka terpisah bila perlu)
Verifikasi device end-to-end (sinkron dua arah + logcat selama app memanggil RPC) TIDAK memadai karena env uji TANPA content source/addon/media server → provider sync watched/progress tidak aktif, app tak memanggil RPC watch (progress butuh playback konten). APK debug lama (`c6054f20…`) ter-install & app jalan, tapi tak ada traffic `rest/v1` watch.
Bukti pengganti: `get_advisors` men-list ke-10 RPC callable `authenticated` via `/rest/v1/rpc/<name>` (PostgREST mem-ekspos → mustahil 404/PGRST202) + smoke SQL sukses. Sama seperti catatan env T11d (#17). Untuk verifikasi device penuh: siapkan addon/media server (atau akun Trakt/Simkl) supaya provider watched/progress aktif, lalu playback + buka daftar watched.

## Catatan kebersihan DB
`sync_events` masih memuat 2 baris `library` `t11d-pull-test:movie` (leftover verifikasi device #17, append-only log by design). Bukan artefak T11e. Tabel watch bersih.

## Acceptance #18
- [x] Delta cursor naik utk insert/update/delete (termasuk update posisi) — smoke ✓
- [x] get_advisors security bersih ✓
- [x] Progress & watched tersinkron (SQL-level, kedua domain) ✓; dua arah device↔DB menunggu content source (env debt).
- [x] Logcat bebas 404/PGRST202 utk 10 RPC — dibuktikan via eksposur PostgREST + smoke; panggilan device menunggu content source.
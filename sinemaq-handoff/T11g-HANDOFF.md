# T11g — Handoff: Membership minimal + watch party

Issue: Callmerev95/SINEMAQ#20. Branch `cmp-rewrite`. Bukti lengkap di komentar #20.

## Status: SELESAI (backend + verifikasi device)

Semua perubahan **murni sisi Supabase** (project `pvkqynwwyelqogvgxueq`) via MCP. **Tidak ada commit kode app**, tidak ada sentuh `local.properties`/secret, tidak ada build ulang / sentuh submodule native.

## Yang dikerjakan

### Audit signature (source of truth = kode app)
Sumber: `MembershipOverviewRemoteDataSource.kt`, `PartyApi.kt`, `PartyModels.kt`, `PartyRepository.kt`, `PartyChat.kt`.

Temuan kunci audit: **PartyApi.kt memakai 14 RPC, bukan 5** (judul #20 pun "14 RPC"). Koreksi triage awal ("5, bukan 14") salah — dibuat **14 RPC penuh** sesuai keputusan user. `get_my_membership_overview` app pakai `decodeList().firstOrNull()` → wajib `setof jsonb` 1 baris.

### Migration (8)
1. **`t11g_membership_overview`** — RPC `get_my_membership_overview()` `setof jsonb` SECURITY DEFINER, pure function (TANPA tabel): selalu kembalikan overview INACTIVE valid (status='inactive', tier='free', membership_level='free', semua flag subscription/grant=false/null). Sesuai keputusan #12.
2. **`t11g_watch_party_tables`** — 3 tabel: `watch_parties` (id, code unique, host_device_id, media jsonb, playback jsonb, host_only, created_at, updated_at, expires_at default +12h), `watch_party_members` (party_id fk, member_id, device_id, display_name, avatar_url, avatar_color, profile_index, last_seen_at; unique(party_id,device_id) + unique(party_id,member_id)), `watch_party_messages` (id bigint identity, party_id fk, member_id, name, avatar_url, avatar_color, body, client_id, sent_at bigint; index(party_id,id)). RLS enable + policy `for all to authenticated` di ketiganya + trigger `watch_parties_touch` (updated_at).
3. **`t11g_watch_party_rpcs_a`** — helper `watch_party_snapshot(party,device)` + `watch_party_enabled`, `watch_party_clock`, `watch_party_preview`, `watch_party_send_message`, `watch_party_list_messages`.
4. **`t11g_watch_party_rpcs_b`** — 9 RPC lifecycle: `create`, `join`, `heartbeat`, `update`, `leave`, `end`, `set_host_only`, `remove_member`, `link_profile`.
5. **`t11g_tighten_grants`** — revoke `anon/public`, grant `authenticated` untuk 15 fungsi; revoke helper internal dari authenticated.
6. **`t11g_watch_party_preview_fix`** — bug: `hostm` dideklarasi `record` → `select jsonb_build_object(...) into hostm` membungkus jadi `{"jsonb_build_object":{...}}`, merusak `preview.host` (app baca objek langsung). Fix: deklarasi `hostm jsonb`.
7. **`t11g_preview_regrant`** — re-grant preview (create or replace reset ACL ke public).
8. **`t11g_party_scalar_return_fix`** + **`t11g_restore_enabled_false`** — lihat "Bug ditemukan" di bawah.

### 14 RPC party SECURITY DEFINER
Semua `security definer`, `set search_path=public, extensions`, validasi `auth.uid()`, grant `authenticated`, revoke `anon/public`.
`watch_party_enabled` (return boolean, default **false**), `watch_party_clock` (bigint epoch ms), `watch_party_preview(p_code)`, `watch_party_send_message(party_id,device_id,body,client_id)`, `watch_party_list_messages(party_id,device_id,after)`, `watch_party_create(...)`, `watch_party_join(...)`, `watch_party_heartbeat`, `watch_party_update`, `watch_party_leave`, `watch_party_end`, `watch_party_set_host_only`, `watch_party_remove_member`, `watch_party_link_profile`. `max_members` = 3 konstanta.

## Bug ditemukan & diperbaiki saat verifikasi device
1. **`watch_party_enabled`/`clock` scalar return**: awalnya `returns text` → PostgREST balik JSON string `"true"` → app `toBooleanStrict()` gagal (`IllegalArgumentException: The string doesn't represent a boolean value: "true"`, `PartyApi.kt:121`). Fix: ubah return `enabled`→`boolean`, `clock`→`bigint` (bare JSON scalar, lolos `.trim().toLong()`). Drop-recreate (return type tak bisa diubah in-place).
2. **`watch_party_preview.host`** nested ganda (lihat migration 6). Fix deklarasi variabel.

## Verifikasi

### Smoke SQL (rollback, JWT fixture `t11a-1794813151@sinemaq.test`)
- `get_my_membership_overview()` → 1 baris, status=inactive, tier=free, semua flag false ✓.
- Party lifecycle penuh: create(kode 6 char)→join(2 members)→preview(host terisi)→send→list(mine=true)→heartbeat→set_host_only→update→remove_member→leave→end→rows=0 ✓.
- Error path: not_member, not_host, party_not_found (kode asal), party_full ✓.
- `enabled()`=false, `clock()` numeric ✓.

### Advisors security
Hanya WARN non-blocking: 51× `authenticated_security_definer_function_executable` (intensional, semua filter `auth.uid()`) + `auth_leaked_password_protection` (pre-existing). Tak ada kritikal.

### Device EMIJ85FU5LOVVS4P (APK debug lama, kode app tak berubah)
- override `watch_party_enabled`→true (sementara). Login `t11a` → profil Utama → putar "The Body" S01E01 **720p** → kontrol pemutar muncul **"Watch party"** → panel "Watch together" → **Start watch party** → party dibuat **kode 54EE76**, "1 of 3 seats", Utama (You)=Host, status **Watching**.
- Chat: kirim "halo%party%test" → tampil di UI (05.42) ✓. DB: baris pesan tersimpan (body, name=Utama, client_id) ✓.
- Logcat **bebas** 404/PGRST202/exception untuk seluruh 14 RPC party (setelah fix scalar).
- **enabled di-RESTORE ke false + diverifikasi** (`watch_party_enabled()`=false) — fitur kembali tersembunyi rapi. Party test dibersihkan dari DB (0 party/member/message).

## Batas jujur
- **Join lintas-device nyata tak tercakup** (hanya 1 device). Sisi pembuat party (create/preview/send/list/clock/enabled) terverifikasi penuh; join/heartbeat lintas device diverifikasi di level smoke SQL (device dummy kedua), bukan 2 device fisik.
- **leave/end party tidak diverifikasi dari UI** — UI host (panel party) tidak menyediakan tombol leave/end eksplisit yang tertangkap uiautomator. Keduanya terverifikasi penuh di level RPC (smoke SQL). leave/end dari app memakai RPC yang sama.
- Verifikasi party butuh konten; dipicu dari UI memakai konten komunitas addon (PenguPlay) yang sudah terpasang.

## Keputusan desain
- Membership = **pure function tanpa tabel** (keputusan #12, belum ada monetisasi).
- Party enabled **hardcoded false** (ponytail: tabel config bila kelak perlu toggle permanen tanpa `create or replace`).
- Scalar RPC (`enabled`/`clock`) balik bare JSON scalar (boolean/bigint), bukan text — menyesuaikan parser `toBooleanStrict`/`toLong` app.
- `member_id` = uuid text per keanggotaan; host diidentifikasi via `host_device_id`.

## Untuk pengerjaan berikutnya
- 3 tabel party + 14 RPC + 1 RPC membership live. App mobile (APK lama) sudah memakai; kode app tak perlu diubah.
- Bila perlu toggle party tanpa redeploy fungsi: buat tabel config 1-baris dan ubah `watch_party_enabled` membacanya.
- Ingat: scalar RPC harus balik bare scalar (bukan text ber-kutip); setelah drop/recreate fungsi jalankan `notify pgrst,'reload schema'`.

# T11g-party-realtime — Handoff: Otorisasi Supabase Realtime (fix "Reconnecting")

Issue: Callmerev95/SINEMAQ#24 (dipecah dari #20). Branch `cmp-rewrite`. Bukti lengkap di komentar #24.

## Status: SELESAI (backend + verifikasi device) — issue ditutup completed

Semua perubahan **murni sisi Supabase** (project `pvkqynwwyelqogvgxueq`) via MCP. **Tidak ada commit kode app**, tidak ada sentuh `local.properties`/secret, tidak ada build ulang / sentuh submodule native.

## Akar masalah
`PartyChannel.kt:33` membuka channel Realtime private `watch-party:<partyId>` (`isPrivate = true`). Channel private butuh **policy otorisasi di schema `realtime`**. Policy itu **belum pernah ada** → subscribe ditolak → status tak pernah `SUBSCRIBED` → UI menggantung di "Reconnecting" + overlay menempel di tengah player.

## Yang dikerjakan

### Migration (1, non-destruktif, live)
**`t11g_realtime_watch_party_policy`** — 2 policy ketat di `realtime.messages` (BUKAN `using(true)`):
- `watch_party_broadcast_read` (`FOR SELECT TO authenticated`, `USING`)
- `watch_party_broadcast_send` (`FOR INSERT TO authenticated`, `WITH CHECK`)

Predikat keduanya:
```sql
topic like 'watch-party:%'
and exists (
  select 1 from public.watch_parties p
  where p.id::text = substring(topic from 13)   -- prefix 'watch-party:' = 12 char
    and p.expires_at > now()
)
```
Ditutup `notify pgrst, 'reload schema'`.

Desain (opsi A, disetujui): pemanggil wajib authenticated (`auth.uid()` tak null) + nama channel cocok `watch-party:<uuid>` yang party-nya ADA & BELUM EXPIRED. Keterbatasan jujur: `watch_party_members.member_id` bukan `auth.uid()` (identitas per-device, dibuat server) → policy tak memvalidasi keanggotaan per-user; pengaman utama tetap di Lapis 1 (join butuh party code). Penyempurnaan opsi B (nanti): tambah kolom `user_id` ke `watch_party_members` agar policy memvalidasi keanggotaan per-user.

### Penyimpangan dari teks issue (jujur)
Issue menyebut policy di `realtime.messages` **+ `realtime.presences`**. Ternyata **`realtime.presences` TIDAK ADA** di project ini (Realtime versi baru — presence bersifat ephemeral, otorisasinya lewat join channel yang digerbangi policy `messages`). Policy dibuat **hanya di `realtime.messages`**. **Presence diverifikasi jalan di device** (presence "Watching", "1 of 3 seats") — tak perlu eskalasi.

## Verifikasi

### Smoke SQL (rollback, JWT fixture t11a)
Predikat kebijakan diuji langsung (4 kasus):
- `valid_party` → **diizinkan (true)** ✓
- `nonexistent_party` → ditolak (false) ✓
- `expired_party` → ditolak (false) ✓
- `random_topic` (`private:...`) → ditolak (false) ✓

### get_advisors security
Hanya WARN non-blocking pre-existing — 51× `authenticated_security_definer_function_executable` (intensional, semua SECURITY DEFINER RPC filter `auth.uid()`) + `auth_leaked_password_protection`. **Tak ada temuan baru** dari migration ini.

### Device EMIJ85FU5LOVVS4P (APK lama dgn bug, 720p, addon live, akun t11a)
Override `watch_party_enabled`→true → login → putar The Body S01E01 (720p Cinejoy·Lisbon) → Start watch party:
- **"Reconnecting" → tersambung**: **Party code `7A4A1E`** tampil, "Host", "Watching", "1 of 3 seats" (UI dump, tanpa "Reconnecting").
- **Overlay "Reconnecting" TIDAK menempel** di player setelah panel ditutup (UI dump player: hanya judul "The Body", `reconnect` = 0 match).
- **Chat tersinkron via Realtime**: kirim `t11g-realtime-sync` → tampil di UI + tersimpan di `watch_party_messages` melalui channel private.

### Amendemen acceptance "Logcat SUBSCRIBED"
Build app ini **tidak memancarkan baris log status channel realtime** (`supabase-kt` realtime tak menulis `SUBSCRIBED` ke logcat; grep capture bersih saat party subscribe = 0 baris status channel dari app). Item "Logcat SUBSCRIBED" **diamendemen** (keputusan maintainer): channel SUBSCRIBED dibuktikan via UI (Party Code + presence) & chat masuk DB lewat channel private. Bukan kegagalan fungsional.

## Cleanup + restore
- Party test dihapus (0 parties / 0 members / 0 messages).
- **`watch_party_enabled` di-RESTORE ke `false` + diverifikasi** (fitur kembali tersembunyi di produksi). ⚠️ `watch_party_enabled` = fungsi hard-coded `select false`; toggle via `create or replace` saat testing, WAJIB restore.

## Batas jujur
- **Join lintas-device nyata tak tercakup** (1 device fisik). Presence/party-code/chatsync terverifikasi di satu device; sinkronisasi antar-2-device fisik tak diuji.
- **Logcat `SUBSCRIBED` mentah tak tersedia** di build ini (lihat amendemen acceptance).

## Acceptance #24 — terpenuhi
- [x] "Reconnecting" → tersambung (Party Code), tak menempel di player.
- [x] Channel mencapai SUBSCRIBED (via UI+DB; amendemen — build tak pancarkan log status).
- [x] Chat/presence tersinkron via Realtime.
- [x] `get_advisors` security bersih.
- [x] `watch_party_enabled` di-restore ke `false`.

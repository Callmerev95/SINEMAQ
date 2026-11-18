# HANDOFF — D2b Watch Party Tier Limits SELESAI

> Status: **SELESAI & terverifikasi di DB**. Project `pvkqynwwyelqogvgxueq`.
> Scope: seat limit party ditentukan tier **host** (Free 3 / Supporter 5 / Lifetime 8), server-authoritative. Tidak ada perubahan Kotlin.

---

## Keputusan terkunci

- **Live tier via `host_user_id`**: limit dihitung ulang tiap panggilan lewat `member_party_limit(host_user_id)` (grant aktif: `is_lifetime or expires_at is null or expires_at > now()`; urut lifetime > supporter > free, lalu `created_at desc`, limit 1; fallback 3). Upgrade/expiry langsung berlaku, konsisten dengan `get_my_member_access` (D2 fase 1).
- **RLS SELECT-only** untuk `watch_parties`, `watch_party_members`, `watch_party_messages`. Klien tidak pernah menulis tabel langsung (semua via RPC `SECURITY DEFINER`; Realtime pakai broadcast) → hardening aman.
- **Tanpa perubahan klien/UI** untuk `party_full`: mapping `party_full` → `PartyError.Full` sudah ada (T11g); seat dots memakai `info.maxMembers`/preview `max_members`.
- **`host_user_id` tanpa FK** ke `auth.users` (konsisten T11h: party data device-scoped, orphan + expire natural). Null (party lama) → fallback 3.
- **Kill-switch durable**: `watch_party_enabled()` membaca `public.app_flags` (key `watch_party`), bukan hardcode. Toggle tanpa redeploy fungsi; nilai saat ini `false` (fitur tersembunyi).

---

## Migrations (applied)

### `d2b_member_party_limit`

`public.member_party_limit(p_user uuid) returns integer`
- SQL, STABLE, SECURITY DEFINER, `search_path=public`.
- Pilih grant aktif user; `lifetime → 8`, `supporter → 5`, else `3`.
- ACL: `postgres` + `service_role` saja (tidak callable langsung oleh authenticated).

### `d2b_watch_party_tier_limits`

- `public.watch_parties.host_user_id uuid` (nullable, tanpa FK).
- `watch_party_create` → insert `host_user_id = auth.uid()`.
- `watch_party_snapshot(uuid,text)` → `max_members` dari helper (fallback 3).
- `watch_party_preview(text)` → cabang sukses pakai helper; cabang `party_not_found` tetap 3 (party tak ada → tak ada host).
- `watch_party_join(text,text,text,text,text,integer)` → `if cnt >= coalesce(member_party_limit(v.host_user_id),3) then 'party_full'`.
- ACL (replace me-reset ACL, lalu di-grant ulang): `create`/`join`/`preview` → `authenticated, service_role`; `snapshot` → `service_role` saja.

### `d2b_party_rls_select_only`

Drop policy ALL lama, ganti:
- `watch_parties_select_authenticated` — FOR SELECT TO authenticated USING (true)
- `watch_party_members_select_authenticated` — FOR SELECT TO authenticated USING (true)
- `watch_party_messages_select_authenticated` — FOR SELECT TO authenticated USING (true)

Tabel milik `postgres` (`relforcerowsecurity=false`) → RPC definer tetap bisa menulis.

### `d2b_app_flags_watch_party_toggle`

Tabel `public.app_flags` (`key text pk`, `enabled bool not null default false`, `description`, `updated_at`).
- RLS enable, **0 policy**, `revoke all` dari `anon`/`authenticated` → tidak bisa dibaca/ditulis lewat PostgREST; tulis hanya via `service_role`/SQL editor.
- Seed `('watch_party', false)`.
- `watch_party_enabled()` (SQL, STABLE, SECURITY DEFINER, `search_path=public, extensions`) membaca baris tersebut, fallback `false` bila baris hilang.
- ACL: `authenticated` + `service_role` (dipakai klien); `anon` tidak.

### `d2b_party_limit_display_clamp`

`max_members` di snapshot & preview = `greatest(live_limit, member_count)`.
Alasan: jika grant host expire saat party berjalan, anggota lama tidak ditendang (join baru diblokir oleh live limit), jadi tampilan tidak boleh "8 of 3 seats". Display-only; enforcement tetap live limit.

---

## Verifikasi smoke (transaksional, data dibersihkan)

### Matriks tier

| Host tier | `max` | Join sukses | Join ditolak |
|---|---|---|---|
| Free | 3 | 2 guest (host+2=3) | join ke-3 `party_full` |
| Supporter | 5 | 4 guest | join ke-5 `party_full` |
| Lifetime | 8 | 7 guest | join ke-8 `party_full` |

- `preview` free: `members=3 max=3`.
- Direct insert sebagai authenticated: **BLOCKED** (RLS SELECT-only).

### Downgrade/expiry saat party berjalan

- Supporter host, 5 anggota (`max=5`).
- Grant supporter di-set `expires_at` lampau (dalam transaksi):
  - `heartbeat` → `max=5 members=5` (clamp; live limit kini 3)
  - `preview` → `max=5 members=5`
  - join baru → `party_full`
- Grant direstore (`expires_at` null), party smoke dihapus.

### State akhir

`parties=0, members=0, messages=0, grants=2 (semua tanpa expiry), watch_party_enabled=false`.
Toggle `app_flags` diuji: `enabled=true` → RPC `true`; direstore ke `false` → RPC `false`.
Advisors security: sama seperti sebelumnya (51 SECURITY DEFINER intentional + leaked password protection), plus satu INFO `rls_enabled_no_policy` pada `app_flags` — disengaja (nol akses klien).

---

## Catatan operasional

- Toggle fitur (tanpa redeploy fungsi):
  ```sql
  update public.app_flags set enabled = true,  updated_at = now() where key = 'watch_party';
  update public.app_flags set enabled = false, updated_at = now() where key = 'watch_party';
  ```
  Klien memuat ulang flag saat app kembali foreground (`PartyRepository.refreshEnabled()`), jadi tak perlu restart paksa.
- **Jangan** biarkan `enabled=true` di produksi tanpa rencana rilis. Tabel bisa diubah hanya via SQL editor/`service_role`; klien tidak punya akses tulis.
- Bila baris `watch_party` hilang, `watch_party_enabled()` = `false` (fail-closed).
- Party lama (sebelum kolom `host_user_id`) → limit 3 sampai host membuat party baru.
- Expiry grant host tidak mengeluarkan anggota; hanya memblokir join baru sampai jumlah turun di bawah limit.
- `member_party_limit` tidak di-expose ke authenticated; jangan grant execute (bocor informasi tier user lain).

## Sisa pekerjaan (opsional)

- Uji device E2E saat flag dinyalakan: buat party per tier, cek "x of y seats" dan `party_full` di UI (tanpa perubahan kode).
- Tidak ada perubahan kode yang perlu di-commit untuk D2b; satu-satunya artefak repo adalah dokumen ini.

# HANDOFF — D2 Fase 1 (Supabase) SELESAI

> Status: **SELESAI & terverifikasi**. Backend `pvkqynwwyelqogvgxueq`.
> Berikutnya: Fase 2 (Kotlin) — sesi terpisah.

---

## Keputusan terkunci

- **Tier case = UPPERCASE** (fix A). SQL emit `FREE`/`SUPPORTER`/`LIFETIME` via `upper(...)`. Cocok parser klien case-sensitive (`MemberTier.entries.firstOrNull { it.name == remote.tier }`).
- **`SUPPORTER_PLUS` dihapus** dari enum Kotlin (Fase 2). DB tak pernah punya nilai itu di `user_grants`.
- **LIFETIME badge** = gradient emas/ungu (Fase 2, `MemberBrandWordmark.badgeStyle()`).
- Bucket member dibuat **manual via dashboard** oleh owner (nama persis, non-public).

---

## Yang sudah dieksekusi

### M1 — Migration `d2_create_user_grants`

Tabel `public.user_grants` (kolom terverifikasi):
`user_id uuid` FK→auth.users cascade, `tier text` check(free/supporter/lifetime), `kind text` default `manual` check(manual/subscription/lifetime/promo), `is_lifetime bool` default false, `expires_at timestamptz` null, `source text` null, `created_at timestamptz` default now(), PK `(user_id, created_at)`.
RLS enable. Policy `user_grants_select_own`: SELECT utk authenticated via `auth.uid()=user_id`. Tidak ada policy tulis → tulis via service_role saja.

### M2 — Migration `d2_rewrite_get_my_member_access`

`get_my_member_access()` (SQL, STABLE, SECURITY DEFINER): pilih grant aktif (`is_lifetime or expires_at is null or expires_at > now()`), urut kekuatan tier (lifetime>supporter>free) lalu `created_at` desc, limit 1, fallback `free`. Return `jsonb_build_object('tier', upper(tier), 'entitlements', [...])`.
- lifetime → GOLD/JADE/ROSE_GOLD/ARCTIC_BLUE/GRAPHITE_THEME + PROFILE_AVATARS + PROFILE_BACKGROUNDS
- supporter → 5 tema + PROFILE_AVATARS (tanpa BACKGROUND)
- free → `[]`

### M3 — Migration `d2_sync_push_profiles_tier_aware`

`sync_push_profiles()` (plpgsql): `v_max` dihitung dari `user_grants` aktif (lifetime 6 / supporter 5 / else 3), `p_client_max_profiles` **diabaikan** (server-authoritative). Clamp lama dihapus. Pertahankan: cek `not authenticated`, validasi `profile_index` 1..6, upsert on-conflict `(user_id, profile_index)`, delete-orphan.

### Storage policy

`member_assets_read_authenticated` pada `storage.objects`: SELECT utk authenticated, `bucket_id in ('membership-profile-avatars','membership-profile-backgrounds')`. Bucket sendiri non-public. (Bucket `avatars` publik tetap tanpa policy.)

---

## Verifikasi (dilakukan)

- Definisi `get_my_member_access` = sesuai M2, `upper()` di tier. ✔
- Policy `user_grants_select_own` + `member_assets_read_authenticated` terpasang, role=authenticated. ✔
- Kolom `user_grants` = sesuai. ✔
- Kedua bucket member: `public=false`, ada. ✔
- Sanity CTE standalone (tanpa data user nyata): supporter+lifetime campur → pilih LIFETIME, emit `LIFETIME`, entitlements lifetime. ✔

**Belum diverifikasi runtime** (butuh JWT `auth.uid()`, MCP tak bisa impersonate):
- Panggilan `get_my_member_access()` nyata per user.
- `sync_push_profiles()` enforce limit per tier.
→ Uji manual di Fase uji: seed user supporter → coba profil ke-6 harus `profile limit exceeded`; seed lifetime → 6 profil lolos.

---

## Cara seed tier user (owner, via SQL editor / service_role)

```sql
-- jadikan supporter
insert into public.user_grants (user_id, tier, kind, is_lifetime, source)
values ('<uuid-user>', 'supporter', 'manual', false, 'manual-grant');
-- jadikan lifetime
insert into public.user_grants (user_id, tier, kind, is_lifetime, source)
values ('<uuid-user>', 'lifetime', 'lifetime', true, 'manual-grant');
-- cek hasil (dari konteks user, bukan service_role):
select * from public.get_my_member_access();
```
Catatan: baris terakhir per user menang bila kekuatan tier sama; lifetime selalu menang atas supporter.

---

## Checklist Fase 2 (Kotlin)

1. `MemberAccess.kt`: enum → `FREE, SUPPORTER, LIFETIME` (hapus `SUPPORTER_PLUS`).
2. `when` arms:
   - `MemberBrandWordmark.kt:55` `badgeStyle()` — tambah FREE + LIFETIME (emas/ungu).
   - `SupporterMembershipCard.kt:407` `displayName()` — tambah FREE + LIFETIME.
   - Test `MemberBrandWordmarkTest.kt:26` (pakai SUPPORTER_PLUS) → update.
   - Test `ThemeAccessTest.kt` loop `MemberTier.entries` → pastikan lolos dgn enum baru.
3. Helper `MemberTier.profileLimit()` (LIFETIME 6 / SUPPORTER 5 / else 3) di `MemberAccess.kt`.
4. Ganti konsumen `MAX_PROFILES` → `profileLimit()`: `NativeTabBridge.kt:156`, `ProfileSwitcherTab.kt:457`, `ProfilePopupContent.kt:89`, `ProfileSelectionScreen.kt:179`, `ProfileRepository.kt:205,225`. Sweep (`ProfileRepository.kt:504`, `LocalAccountDataCleaner.kt:46`, `PlatformLocalAccountDataCleaner.ios.kt:79`) tetap `MAX_PROFILES`.
5. Soft prompt "batas profil tercapai → upgrade" buka `CommunityConfig.UPGRADE_URL`.
6. `build.gradle.kts:194-208` tambah `UPGRADE_URL` di `CommunityConfig` + `local.properties.example`.
7. Kosmetik: **no change** (sudah entitlement-gated).
8. Build `assembleFullDebug` + `testAndroid` (abaikan `WatchProgressIdentityTest` pre-existing).

## Belum selesai (nyusul, out Fase 1)

- Seed catalog avatar/background member + upload webp contoh ke bucket member (handoff 3.5).
- Fase uji manual runtime RPC.
- D2b (watch party tier), D3-TV (TV-link), webhook — out of scope.

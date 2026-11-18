# HANDOFF — D2: Monetisasi 3-Tier (Free / Supporter / Lifetime)

> Status desain: **DISETUJUI (grilled)**. Belum dieksekusi. Sesi ini untuk eksekusi.
> Repo: SINEMAQ (fork NuvioMobile). Backend: Supabase `pvkqynwwyelqogvgxueq` (owner-managed).

---

## 1. Konteks & Kenapa Ini Ada

Owner ingin memonetisasi SINEMAQ dengan model **3 tier** yang berbeda dari Nuvio (Nuvio = subscription Patreon; SINEMAQ = tier + opsi one-time/lifetime). Konsep membership di kode sudah tersiapkan penuh tapi masih kosong:

- RPC `get_my_membership_overview` = **stub tanpa tabel**, selalu return inactive/free (keputusan ticket #12: "belum ada monetisasi").
- Enum `MemberTier` = `SUPPORTER`, `SUPPORTER_PLUS` (belum ada `FREE`/`LIFETIME`).
- Klien sudah punya pluming penuh untuk gating kosmetik via `CosmeticEntitlement` (`.includes()`), tapi selalu kosong karena RPC return free.

**Kenapa tidak salin upstream:** client_id baru owner = **AUTH V2**; upstream pakai V1. Salin kode upstream akan mereproduksi bug (V2 creds ditolak endpoint V1).

---

## 2. Desain Final (Disepakati via Grilling)

### Tier & Batas

| Fitur | Free | Supporter | Lifetime |
|---|---|---|---|
| Profil maks | 3 | 5 | 6 |
| Watch party (tier HOST menentukan) | 3 | 5 | 8 |
| Avatar premium | ❌ | ✅ | ✅ |
| Profile background | ❌ | ❌ | ✅ |
| Tema warna premium (5) | ❌ | ✅ | ✅ |

### Keputusan Arsitektur (Q1–Q15)

1. **Model**: 3 tier (bukan subscription berulang). Lifetime = one-time/lifetime grant.
2. **Enforcement**:
   - **Profil** → **server-side** (RPC `sync_push_profiles` memvalidasi batas dari tier di DB). Q6/Q8.
   - **Watch party** → **DITUNDA** ke tiket D2b (server-authoritative, butuh ubah RPC party). Q14.
   - **Avatar/background katalog** → client-side + Storage RLS bucket (sudah ada). Q8.
3. **Upgrade tier** = **manual oleh owner** via tabel Supabase (tanpa webhook dulu). Q7.
4. **TV-link login** → **scope-out** dari D2, jadi tiket terpisah D3-TV. Q12.
5. **Upgrade URL** → **configurable** via `UPGRADE_URL` di `local.properties` (pola `DONATIONS_URL`). Q13.
6. **Semua tier di-enforce** (Free 3, Supporter 5, Lifetime 6) — `get_my_member_access` mengembalikan tier + RPC memvalidasi. Q10.
7. **UI saat batas tercapai** = **soft prompt** (dialog "Upgrade ke Supporter untuk profil ke-4") yang membuka `UPGRADE_URL` jika dikonfigurasi. Q11.
8. **Eksekusi D2** = Supabase (MCP) + Kotlin app **keduanya** dalam satu tiket. Q15.
9. **Penamaan tier**: `Free`, `Supporter`, `Lifetime` (bukan "One-time" / `SUPPORTER_PLUS`). Q3.
10. **Angka**: profil **3/5/6**; watch party **3/5/8** (ditunda D2b). Q3.

---

## 3. Rencana Eksekusi — Supabase (via MCP, `pvkqynwwyelqogvgxueq`)

### 3.1 Migration: buat tabel `user_grants`

**Belum ada** (terverifikasi). Buat dengan:

```sql
create table if not exists public.user_grants (
  user_id uuid not null references auth.users(id) on delete cascade,
  tier text not null check (tier in ('free','supporter','lifetime')),
  kind text not null default 'manual' check (kind in ('manual','subscription','lifetime','promo')),
  is_lifetime boolean not null default false,
  expires_at timestamptz,
  source text,
  created_at timestamptz not null default now(),
  primary key (user_id, created_at)
);
alter table public.user_grants enable row level security;

-- Klien hanya boleh baca tier miliknya sendiri; tulis via owner/service_role (bukan anon/authenticated).
create policy "user_grants_select_own" on public.user_grants
  for select to authenticated
  using (auth.uid() = user_id);
-- Tidak ada policy INSERT/UPDATE untuk authenticated → hanya service_role (owner) yang bisa tulis.
```

**Seed default** (opsional, untuk semua user existing jadi Free): bisa dilewati karena fungsi akan fallback ke 'free' bila tak ada baris.

### 3.2 Migration: rewrite `get_my_member_access`

Fungsi existing = stub `select jsonb_build_object('tier','free','entitlements','[]'::jsonb)`. Ganti agar baca tier aktif dari `user_grants`:

```sql
create or replace function public.get_my_member_access()
returns setof jsonb
language sql
stable
security definer
set search_path to 'public'
as $function$
  with active as (
    select tier
    from public.user_grants
    where user_id = auth.uid()
      and (is_lifetime or expires_at is null or expires_at > now())
    order by
      case tier when 'lifetime' then 3 when 'supporter' then 2 else 1 end desc,
      created_at desc
    limit 1
  ),
  tier_val as (
    select coalesce((select tier from active), 'free') as tier
  )
  select jsonb_build_object(
    'tier', (select tier from tier_val),
    'entitlements', (
      select case (select tier from tier_val)
        when 'lifetime' then '["GOLD_THEME","JADE_THEME","ROSE_GOLD_THEME","ARCTIC_BLUE_THEME","GRAPHITE_THEME","PROFILE_AVATARS","PROFILE_BACKGROUNDS"]'::jsonb
        when 'supporter' then '["GOLD_THEME","JADE_THEME","ROSE_GOLD_THEME","ARCTIC_BLUE_THEME","GRAPHITE_THEME","PROFILE_AVATARS"]'::jsonb
        else '[]'::jsonb
      end
    )
  );
$function$;
```

**Kredensial enum** — string tier kini `"lifetime"`, bukan `"SUPPORTER_PLUS"`. Klien akan dimodifikasi agar membaca `"lifetime"` → `MemberTier.LIFETIME` (lihat 4.1).

### 3.3 Migration: ubah `sync_push_profiles` jadi tier-aware

Fungsi existing (terverifikasi) menghitung `v_max = coalesce(p_client_max_profiles, 6)` + clamp 0..6 + `raise 'profile limit exceeded'`. **Ubah** agar `v_max` dihitung dari tier user (abaikan nilai klien, hindari client trust):

```sql
-- di dalam declare, tambah:
  v_tier text;
-- setelah v_user := auth.uid(); ganti perhitungan v_max menjadi:
  select coalesce(max(
    case g.tier
      when 'lifetime' then 6
      when 'supporter' then 5
      else 3
    end), 3)
  into v_max
  from (
    select tier from public.user_grants
    where user_id = v_user
      and (is_lifetime or expires_at is null or expires_at > now())
    order by case tier when 'lifetime' then 3 when 'supporter' then 2 else 1 end desc,
             created_at desc
    limit 1
  ) g;
-- (hapus/hindari clamp "if v_max < 0 or v_max > 6 then v_max := 6")
```

> Catatan: `p_client_max_profiles` jadi tak dipakai untuk keputusan (klien tetap kirim, tapi server memakai nilai dari `user_grants`). Batas atas Lifetime = 6, cocok dengan `MAX_PROFILES`.

### 3.4 Storage: buat 2 bucket member

**Belum ada** (terverifikasi; hanya `avatars` publik ada):

- `membership-profile-avatars` — non-publik, download authenticated.
- `membership-profile-backgrounds` — non-publik, download authenticated.

Buat via Supabase MCP (`supabase_create_bucket`-setara) atau Storage API; set **public = false**. Tambahkan Storage RLS/policy agar role `authenticated` boleh membaca objek di bucket ini (dipakai `downloadAuthenticated`).

### 3.5 Seed data catalog (opsional tapi disarankan)

Tabel `avatars` & `profile_backgrounds` **sudah ada** (dari ticket T11c) dengan seed standar + 1 member avatar + 2 backgrounds (per T11c-assets-HANDOFF). Verifikasi baris member ada; jika belum, seed minimal:
- 1–6 baris di `avatars` untuk avatar member (`category='member'` atau sesuai konvensi yang dipakai `get_member_profile_avatar_catalog`).
- 1–2 baris di `profile_backgrounds`.

Upload 1–2 file webp ke bucket member sebagai aset contoh agar UI bisa diuji.

---

## 4. Rencana Eksekusi — Kotlin App

### 4.1 Tambah `FREE` + `LIFETIME` ke `MemberTier`

`composeApp/src/commonMain/kotlin/com/nuvio/app/features/membership/MemberAccess.kt:3-6`:
```kotlin
enum class MemberTier {
    FREE,
    SUPPORTER,
    LIFETIME,
}
```
Parsing string → enum (di repository/remote data source) harus memetakan `"lifetime"`→`LIFETIME`, `"free"`→`FREE`, `"supporter"`→`SUPPORTER`.

### 4.2 Tambahkan arm `LIFETIME` di `when` enum (WAJIB — kompilasi gagal tanpa ini)

- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/MemberBrandWordmark.kt:55` (`badgeStyle()`) — `when` di atas `MemberTier`.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SupporterMembershipCard.kt:407-409` (`displayName()`).

Tambahkan `MemberTier.FREE` dan `MemberTier.LIFETIME` di setiap `when` yang exhaustif.

### 4.3 Profile limit tier-aware

Batas kini **bukan** `MAX_PROFILES` konstan, tapi dari tier. `MAX_PROFILES=6` tetap sebagai ceiling sweep.

Helper baru (mis. di `ProfileModels.kt` atau `MemberAccess.kt`):
```kotlin
fun MemberTier?.profileLimit(): Int = when (this) {
    MemberTier.LIFETIME -> 6
    MemberTier.SUPPORTER -> 5
    else -> 3  // FREE / null
}
```

Ganti konsumen `MAX_PROFILES` (8 file, 10+ titik — lihat daftar di bawah) memakai `MemberAccessRepository.access.value.tier.profileLimit()`:

| File:Line | Penggunaan | Aksi |
|---|---|---|
| `ProfileRepository.kt:205` | RPC param `p_client_max_profiles` | Kirim `profileLimit()` (atau biarkan; server memakai nilai DB sendiri) |
| `ProfileRepository.kt:225` | alokasi index `createProfile` | pakai `profileLimit()` |
| `ProfileRepository.kt:504` | PIN sweep loop | biarkan `MAX_PROFILES` (ceiling) |
| `NativeTabBridge.kt:156` | `canAddProfile` | `profileLimit()` |
| `ProfileSwitcherTab.kt:457` | sembunyikan tombol Add | `profileLimit()` |
| `ProfilePopupContent.kt:89` | slot Add | `profileLimit()` |
| `ProfileSelectionScreen.kt:179` | hitungan item | `profileLimit()` |
| `LocalAccountDataCleaner.kt:46`, `PlatformLocalAccountDataCleaner.ios.kt:79` | cleanup sweep | biarkan `MAX_PROFILES` |

**Soft prompt (Q11)**: di titik `createProfile` (ketika `profiles.size >= profileLimit()`), tampilkan dialog "Batas profil tercapai — upgrade" yang membuka `CommunityConfig.UPGRADE_URL` (jika tak kosong). Lokasi UI: `ProfileSwitcherTab.kt` / `ProfileSelectionScreen.kt` (pilih satu titik masuk tambah-profil).

### 4.4 Tambah `UPGRADE_URL`

Ikuti pola `DONATIONS_URL` (build.gradle.kts:194-208 generate `CommunityConfig.kt`):

- `composeApp/build.gradle.kts` — di blok `CommunityConfig` object literal tambahkan:
  ```kotlin
  const val UPGRADE_URL = "${props.getProperty("UPGRADE_URL", "")}"
  ```
- `local.properties.example` — tambah baris `UPGRADE_URL=` + komentar.
- `local.properties` (lokal, gitignored) — isi URL upgrade (mis. halaman web SINEMAQ / Ko-fi).

### 4.5 Kosmetik (avatar/background/tema) — TIDAK perlu perubahan klien

Sudah digerbangi via `entitlements.includes(CosmeticEntitlement.X)`. Setelah RPC V2 mengembalikan entitlement dari `user_grants`, item premium otomatis muncul:
- `AvatarRepository.kt:146,151` (`PROFILE_AVATARS`)
- `ProfileEditScreen.kt:90`, `ProfileBackground.kt:14`, `MemberAccessRepository.kt:145` (`PROFILE_BACKGROUNDS`)
- `ThemeAccess.kt:6-24` + `resolveCustomThemeColors:40-41` (tema; `LIFETIME` non-null sudah memenuhi gradient)

**Bucket member** wajib ada + punya Storage RLS `authenticated` read (3.4) agar `downloadAuthenticated` berhasil.

---

## 5. Urutan Eksekusi Disarankan

1. **Supabase (MCP)**: migration 3.1 (tabel) → 3.2 (member access) → 3.3 (profile limit). Verifikasi dengan `select * from get_my_member_access();` setelah seed 1 baris `user_grants`.
2. **Supabase (MCP)**: buat 2 bucket member (3.4) + seed catalog/avatar contoh (3.5).
3. **Kotlin**: 4.1 enum → 4.2 `when` arms → 4.3 profile limit + soft prompt → 4.4 UPGRADE_URL.
4. **Build & verify**: `assembleFullDebug` BUILD SUCCESSFUL + `testAndroid` (hanya failure pre-existing `WatchProgressIdentityTest` boleh).
5. **Commit + push** ke `main` (prefix `D2:`).
6. **Uji manual**: 
   - Seed user jadi `supporter` → cek 5 profil, avatar premium muncul, tema premium terbuka, background tetap terkunci.
   - Seed user jadi `lifetime` → 6 profil + background terbuka.
   - Free → soft prompt saat tambah profil ke-4.

---

## 6. Out of Scope (Tiket Terpisah)

- **D2b — Watch party tier-based** (3/5/8, tier host). Server-authoritative: ubah `watch_party_create`/`watch_party_join` agar `max_members` mengikuti tier host. Klien hanya baca `max_members`. Tunda per keputusan Q14.
- **D3-TV — TV-link login** (3 RPC device/TV-link + edge `tv-logins-exchange`). Q12 scope-out.
- **Webhook pembayaran** — saat skala butuh otomatisasi (Q7: sekarang manual).
- **Enforcement server watch party / katalog** — tambahkan saat ada bukti penyalahgunaan (Q8).

---

## 7. Fakta Backend Saat Ini (terverifikasi via MCP `pvkqynwwyelqogvgxueq`)

- Tabel `user_grants`: **belum ada**.
- Bucket: hanya `avatars` (publik). `membership-profile-avatars`/`-backgrounds`: **belum ada**.
- `get_my_member_access()`: stub `select jsonb_build_object('tier','free','entitlements','[]'::jsonb)`, STABLE SECURITY DEFINER.
- `sync_push_profiles(p_client_max_profiles int, p_profiles jsonb, p_origin_client_id text)`: sudah melempar `'profile limit exceeded'` bila count > v_max; index dicek 1..6; upsert `profiles` on conflict `(user_id, profile_index)`.

## 8. Fakta Klien Saat Ini

- `MAX_PROFILES = 6` di `ProfileModels.kt:9`; dipakai di 8 file / 10+ titik (daftar di 4.3).
- `MemberTier` = `SUPPORTER`, `SUPPORTER_PLUS` (tanpa FREE/LIFETIME) di `MemberAccess.kt:3-6`.
- `MemberAccessResponse = {tier: String, entitlements: List<String>}` di `MemberAccessRemoteDataSource.kt:26-28` — **shape tak perlu diubah**; hanya body RPC yang di-rewrite.
- Kosmetik digerbangi via `entitlements.includes(CosmeticEntitlement.X)` (avatar `AvatarRepository.kt:146,151`; background `ProfileEditScreen.kt:90`, `ProfileBackground.kt:14`; tema `ThemeAccess.kt`).
- `CommunityConfig` di-generate dari `local.properties` via `composeApp/build.gradle.kts:194-208`.

## 9. Jangan Lupa

- **Jangan** ganti scheme `nuvio://` — OAuth Trakt/Simkl + party bergantung padanya.
- **Jangan** sentuh `NUVIO_ENGINE_*` / `WatchProgressSource.NUVIO_SYNC` (konstanta interop/storage).
- Kredensial di `local.properties` (gitignored) — jangan pernah commit.
- Pre-existing failure: `WatchProgressIdentityTest` di `testAndroidHostTest` (abaikan).

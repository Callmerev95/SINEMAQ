# HANDOFF — D2 Fase 2 (Kotlin) SELESAI

> Status: **SELESAI & build lolos**. Backend Fase 1 + Klien Fase 2 = D2 selesai (kecuali yang di-out-of-scope).
> Repo: SINEMAQ (fork NuvioMobile). Backend Supabase `pvkqynwwyelqogvgxueq`.

---

## Ringkasan eksekusi Fase 2

### 1. Enum + helper (`MemberAccess.kt`)
- `MemberTier` → `FREE, SUPPORTER, LIFETIME` (`SUPPORTER_PLUS` dihapus).
- Tambah `fun MemberTier?.profileLimit(): Int` → LIFETIME 6 / SUPPORTER 5 / else 3.
- Parser `MemberAccessRemoteDataSource` & cache repo **tidak diubah** — sudah case-sensitive `entries.firstOrNull { it.name == remote.tier }`, cocok dengan UPPERCASE dari Fase 1.

### 2. Exhaustive `when` (wajib agar kompilasi lolos)
- `MemberBrandWordmark.badgeStyle()`:
  - LIFETIME → label `"Lifetime"`, colorStops emas→ungu `[0,0xFFD4843D; 0.45,0xFFFFDE90; 0.75,0xFFB57BFF; 1,0xFF9C6BFF]`.
  - FREE → label `"Free"`, colorStops abu-abu `[0,0xFF7A7F87; 0.5,0xFFB6BAC0; 1,0xFF7A7F87]`.
- `SupporterMembershipCard.displayName()`:
  - LIFETIME → `community_membership_tier_lifetime` = "Lifetime".
  - FREE → `community_membership_tier_free` = "Free".

### 3. Strings (`values/strings.xml` base English)
Tambah: `community_membership_tier_lifetime`, `community_membership_tier_free`, `profile_limit_upgrade_title`, `profile_limit_upgrade_body` (%1$d), `profile_limit_upgrade_cta`, `profile_limit_upgrade_dismiss`.
`community_membership_tier_supporter_plus` **dibiarkan** di base + 22 locale (dead code, keputusan grill #3).

### 4. Profile limit — ganti `MAX_PROFILES` → `profileLimit()`
- `ProfileRepository.pushProfiles` kirim `p_client_max_profiles = profileLimit()` (server tetap authoritative).
- `ProfileRepository.createProfile` sweep `1..profileLimit()`.
- `NativeTabBridge.nativeState` `canAddProfile = profiles.size < profileLimit()`.
- `ProfilePopupContent` slot Add pakai `profileLimit()` (reaktif via collectAsState).
- Sweep **tetap** `MAX_PROFILES` (ceiling): `ProfileRepository.kt:508`, `LocalAccountDataCleaner`, `PlatformLocalAccountDataCleaner.ios`.

### 5. Soft prompt "batas profil tercapai"
Shared component **`ProfileLimitUpgradeDialog.kt`** (AlertDialog M3):
- Tombol Upgrade muncul hanya bila `CommunityConfig.UPGRADE_URL.isNotBlank()`; onClick `uriHandler.openUri(...)`.
- Tombol "Not now" tutup dialog.

Intercept di **2 jalur**:
- `ProfileSwitcherTab` + `NativeProfileSwitcherPopup` (popup tab bar): cek `profiles.size >= profileLimit` sebelum `onAddProfileRequested()`.
- `ProfileSelectionScreen` (edit-mode add, tablet+phone): cek sama sebelum `onAddProfile()`.

### 6. `UPGRADE_URL` config
- `composeApp/build.gradle.kts` blok `CommunityConfig`: tambah `const val UPGRADE_URL = "${props.getProperty("UPGRADE_URL", "")}"`.
- `local.properties.example`: tambah `UPGRADE_URL=` di blok membership.
- `local.properties` lokal: **owner isi sendiri** (gitignored, jangan commit).

### 7. Test
- `MemberBrandWordmarkTest`: ganti case SUPPORTER_PLUS → LIFETIME (label + colorStops baru) + tambah case FREE.
- `ThemeAccessTest`: loop `MemberTier.entries` otomatis lolos; tak diubah.

---

## Verifikasi (dilakukan)

- `:androidApp:assembleFullDebug --rerun-tasks` → **BUILD SUCCESSFUL** (clean rebuild, 5m 20s). ✔
- `:composeApp:testAndroidHostTest` → **2079 completed, 1 failed (WatchProgressIdentityTest — PRE-EXISTING, diabaikan), 8 skipped**. ✔
- `MemberBrandWordmarkTest` & `ThemeAccessTest` lolos. ✔

---

## Belum selesai (nyusul / out of scope)

### Owner-side (Supabase Fase 1 lanjutan)
- **Seed catalog avatar/background member + upload webp contoh** ke bucket `membership-profile-avatars` / `membership-profile-backgrounds` (handoff 3.5). Wajib agar UI premium avatar/background bisa diuji.

### Uji runtime manual (butuh JWT)
- Seed user supporter → cek 5 profil, avatar premium muncul, tema premium terbuka, background tetap terkunci.
- Seed user lifetime → 6 profil + background terbuka.
- Free → soft prompt muncul saat add profil ke-4 (via tab-bar popup & edit-mode).
- Owner isi `UPGRADE_URL` di `local.properties` → dialog tombol Upgrade muncul.

### Tiket terpisah
- **D2b** — watch party tier-based (3/5/8).
- **D3-TV** — TV-link login.
- Webhook pembayaran.
- Terjemahan locale `community_membership_tier_lifetime` / `tier_free` (sekarang fallback English).

---

## Jangan lupa
- Jangan ganti scheme `nuvio://`.
- Jangan sentuh `NUVIO_ENGINE_*` / `WatchProgressSource.NUVIO_SYNC`.
- Kredensial di `local.properties` — jangan commit.
- Pre-existing failure `WatchProgressIdentityTest` — abaikan.

## Commit
- Siap di-commit ke `main` dengan prefix `D2:`. Belum di-commit (menunggu owner review).

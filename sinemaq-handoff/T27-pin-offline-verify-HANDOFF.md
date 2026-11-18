# HANDOFF — T-fix: PIN profil gagal diverifikasi offline (#27)

## Status: SELESAI (kode di working tree, BELUM di-commit)

Issue: https://github.com/Callmerev95/SINEMAQ/issues/27 (CLOSED, label `ready-for-agent`, `priority: high`, assignee @me).

Bug: setelah pasang PIN → tutup app → buka lagi → input PIN benar → ditolak dengan
*"PIN ini belum bisa diverifikasi offline di perangkat ini. Hubungkan sekali dan buka kunci
secara online terlebih dahulu."* PIN valid pun gagal saat offline.

## Akar masalah

Cache PIN offline (`ProfilePinCacheStorage`, payload `CachedProfilePinPayload{salt,digest,profileUpdatedAt}`)
ter-invalidate berlebihan. `profile.updatedAt` naik pada EDIT profil APA PUN (nama/avatar/background)
dan pada pull server tertentu — bukan hanya saat PIN berubah. Baik `syncPinCache` maupun
`verifyPinLocally` MENGHAPUS cache setiap `cached.profileUpdatedAt != profile.updatedAt`, jadi
setelah edit ringan + restart app cache hilang → `verifyPinLocally` jatuh ke cabang payload-kosong
→ pesan "requires online". Digest SHA-256 sebenarnya masih valid.

## Perubahan — `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileRepository.kt`

1. `pullProfiles()` — panggil `syncPinCache(_state.value.profiles)` setelah state di-refresh
   (sebelum `persist()`), jaga state memori & cache sinkron tiap pull server.
2. `verifyPinLocally()` — HAPUS blok cek mismatch `updatedAt` yang meng-hard-fail. Perbandingan
   digest SHA-256 adalah gerbang kebenaran yang sebenarnya (PIN salah → `pin_incorrect`).
   Mematikan false-positive invalidation dari edit nama/avatar.
3. `syncPinCache()` — kini hanya hapus entry cache saat profil hilang / `pinEnabled=false` /
   JSON korup. Tidak ada lagi penghapusan karena mismatch `updatedAt`.

Net: digest offline bertahan melewati edit nama/avatar + restart. PIN server yang benar-benar
berubah tak bisa dipenuhi offline oleh digest lama; verify/set online berikutnya menimpa cache
via `rememberVerifiedPin`.

## Verifikasi

- `:composeApp:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
- `:composeApp:testAndroid` → 2078 completed, 1 failed, 8 skipped.
  Satu-satunya failure = `WatchProgressIdentityTest > legacy payload derives episode progress key...`
  — PRE-EXISTING & TAK TERKAIT (menguji identitas payload watch-progress, tidak menyentuh
  `ProfileRepository.kt`). Terkonfirmasi gagal independen dari diff ini.

## Tidak dilakukan / follow-up

- Belum ada commit. Perubahan ada di working tree (`git diff` pada `ProfileRepository.kt`).
  File lain yang tampak modified (`.gitignore`, `AGENTS.md`, `README.md`, `ProfileModels.kt`)
  adalah perubahan working-tree PRA-EXISTING, bukan dari sesi ini — JANGAN di-commit bareng.
- Belum ada unit test untuk alur cache PIN. `ProfileRepository` singleton terikat ke
  `SupabaseProvider`/`ProfilePinCacheStorage` (expect/actual) tanpa seam untuk menguji
  verifikasi offline. Acceptance divalidasi lewat penalaran + repro manual, bukan test CI.
  Menambah seam yang bisa dites = follow-up.
- Jangka panjang: key invalidation berbasis sinyal spesifik-PIN (mis. `pin_updated_at`
  di server) lebih robust daripada `profile.updatedAt` global, tapi butuh perubahan skema.

## Anti-recurrence (agen berikut)

- Jangan pakai `profile.updatedAt` sebagai kunci invalidasi cache PIN — terlalu rapuh.
- Jangan hapus cache offline hanya karena `updatedAt` beda; biarkan digest memutuskan.
- `syncPinCache` HANYA dipanggil dari `applyPayloadsLocally`, `applyStoredPayload`, dan
  (kini) `pullProfiles` — sadari jalur mana yang memicunya saat mengubah alur profil.

## Skill yang disarankan

- `code-review` — review diff `ProfileRepository.kt` terhadap standar repo sebelum commit.
- `handoff` — perbarui dokumen ini bila ada follow-up lanjutan.

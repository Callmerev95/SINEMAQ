# T11c-assets — Handoff

**Issue**: Callmerev95/SINEMAQ#16 (T11c lanjutan — isi storage + seed agar avatar tampil sbg gambar)
**Branch**: `cmp-rewrite`
**Tanggal**: 2026-11-17
**Status**: SELESAI — acceptance terpenuhi.

## Tujuan
T11c sebelumnya menutup dengan batas jujur: katalog avatar dirender sbg **warna** krn storage bucket kosong. Sesi ini mengisi bucket + seed DB supaya 30 avatar katalog tampil sebagai **gambar**.

## Yang dikerjakan
1. **`.gitignore`** — tambah `sinemaq-avatar/`, `*.webp`, `avatars.csv/json/zip`. Aset seed hidup di Supabase Storage, bukan repo. Tidak ada commit kode app lain.
2. **Supabase Storage** (project `pvkqynwwyelqogvgxueq`) — bucket `avatars` (public) diisi **30 webp (512×512)** via dashboard oleh owner, path `{category}/{file}`. Di luar jangkauan MCP/`apply_migration`.
3. **Migration `t11c_assets_seed`** — `DELETE FROM avatars` (buang 5 seed lama incl. `av_member_gold`/`membership/gold.png`), INSERT 30 baris baru (semua standar, tanpa gate member): `display_name=name`, `storage_path='{kategori}/{file}'`, `category` pendek, `sort_order=no`, `is_active=true`, `bg_color` dr CSV, `asset_version=1`. + `NOTIFY pgrst,'reload schema'`.

## Pemetaan kategori (CSV label panjang → pendek)
| CSV | pendek | jumlah |
|---|---|---|
| Classics / Minimalist | classics | 6 |
| Sci-Fi & Cyberpunk | scifi | 6 |
| Anime / Stylized | anime | 6 |
| Animals / Cute | animal | 6 |
| Movies / Pop Culture Vibe | pop | 6 |

## Verifikasi
**Backend**:
- Bucket `avatars` `public=true`; `storage.objects` = 30 file, 6/kategori, nama path **persis** cocok CSV (case-sensitive).
- Tabel `avatars` = 30 baris, 5 kategori × 6, semua `storage_path` berformat `kategori/file.webp`.
- URL public langsung (`{backendUrl}/storage/v1/object/public/avatars/...`) → **200 image/webp** utk sampel classics/scifi/pop.

**Device EMIJ85FU5LOVVS4P** (jalur: login `t11a` → Pengaturan → Ganti Profil → kelola → edit "Utama"):
- Picker avatar menampilkan **30 avatar sebagai gambar** dr bucket (kucing, rubah, beruang, kelinci, panda, katak, bot, astronaut, detektif, kapten, luchador, wizard, dll.) — bukan fallback warna.
- Logcat: **nol** PGRST202/404 terkait avatar/`get_avatar_catalog`. PGRST202 tersisa hanya pre-existing (Party ×2, DeviceSessionRegistration ×2).

Bukti visual: `/tmp/sq_07.png` (atas grid), `/tmp/sq_08.png` (grid penuh 30 avatar).

## Acceptance
- [x] Picker menampilkan 30 avatar sbg gambar dr bucket.
- [x] 5 kategori × 6 terisi; `bg_color` per avatar sesuai CSV.
- [x] Logcat bebas 404/PGRST202 utk `get_avatar_catalog`.

## Keputusan / catatan
- Hapus **semua** 5 seed lama termasuk avatar member (`av_member_gold`) — disepakati owner; 30 avatar baru semuanya standar.
- `bg_color` tetap disimpan (dipakai sbg latar/placeholder saat gambar loading), tapi render utama kini gambar, bukan blok warna.
- Bucket + upload via dashboard (owner), bukan service_role key / MCP — sesuai kesepakatan.

## Batas jujur
- `avatar_id` belum otomatis tersimpan ke profil via tap (jalur simpan = `sync_push_profiles` T11a, sudah terbukti). Sesi ini fokus **render katalog**, bukan persist pilihan.
- Tidak ada build ulang / sentuh submodule native / secret.

## Komit
- Tidak ada commit (kode app tak berubah; `.gitignore` edit belum di-commit — biarkan owner commit bila mau).
# Sinemaq - Animated Logo + Teks (Netflix theme)

Durasi animasi: 1480ms | Ukuran logo: 334x81dp (rasio 1669:406)

File utama:
- res/drawable/ic_sinemaq_logo_animated.xml  -> AnimatedVectorDrawable (simbol + teks SINEMAQ)
- res/drawable/ic_sinemaq_logo.xml           -> versi statis (posisi akhir animasi)
- res/drawable/ic_splash_empty.xml           -> icon transparan untuk splash sistem
- res/layout/activity_splash.xml, kotlin/SplashActivity.kt, AndroidManifest-snippet.xml, res/values/*

Alur: splash sistem (polos charcoal) -> SplashActivity memutar logo animasi -> MainActivity.

Kenapa tidak di splash bawaan sistem? Icon splash Android 12+ dipotong lingkaran & maks 1000ms,
jadi logo lebar + teks tidak muat. Teks dibuat sebagai vector path (bukan font) sehingga tajam
di semua ukuran layar dan tidak butuh file font.

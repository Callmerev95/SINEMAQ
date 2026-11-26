# SINEMAQ

A media streaming app for Android/iOS/TV, rebranded from [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) (Kotlin Multiplatform + Compose Multiplatform, GPLv3).

## Project facts

- **Upstream origin**: NuvioMedia/NuvioMobile (GPLv3). This fork keeps GPLv3; upstream attribution lives in repo docs/README only, not in app UI. In-app license row links to this fork's source.
- **Brand**: SINEMAQ. Application ID `com.sinemaq.app` (debug `com.sinemaqdebug.com`), iOS bundle `com.sinemaq.media`.
- **Internal Kotlin packages stay `com.nuvio.app`** (deliberate — minimises rebrand risk).
- **Build** (Android): `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/Contents/Home ./gradlew :androidApp:assembleFullDebug`
- **Submodules**: `MPVKit`, `libass-android` are git submodules, **not needed for Android builds** — native `.so` come from prebuilt AARs in `composeApp/libs/` (nuvio-libass, lib-nuvio-engine, lib-*) plus Maven `io.github.abdallahmehiz:mpv-android-lib`. Empty submodules are NOT an Android blocker; only fetch them for iOS.
- **Secrets/keys** live in `local.properties` (gitignored). Never commit real API keys.

## Upstream sync

- Baseline: upstream `cmp-rewrite` @ `0d20af83`, merge `4cedec04`; versi app `1.1.0` (162).
- Remote `upstream` ada; push dinonaktifkan (`no_push`).
- Upstream `main` = jalur stabil dan tertinggal dari `cmp-rewrite`; cek keduanya sebelum sync.
- Delta fork yang wajib dipertahankan: `store-beta.json` absen + guard `[[ -f "${source}" ]] || continue` + loop `git add`; gate prerelease di `android-release.yml` dan `update-store-source.yml`; secret `SINEMAQ_*`; `store.json`/`README.md` milik fork; `discontinue-legacy-main.yml` dihapus.
- Test unit Android: `:composeApp:testAndroidHostTest` (tidak ada `testDebugUnitTest`).
- Identitas klien media server = SINEMAQ; marker internal (`#nuvio-plex=`, paket `com.nuvio.*`) tetap.
- Known exception: UA MDBList tetap `Nuvio/<versi>`.

## Agent skills

### Issue tracker

Issues live on GitHub at `Callmerev95/SINEMAQ`. See `Docs/agents/issue-tracker.md`.

### Triage labels

Custom vocabulary: five canonical state roles plus supporting category/priority/resolution labels. See `Docs/agents/triage-labels.md`.

### Domain docs

Single-context layout. `CONTEXT.md` and `docs/adr/` are created lazily by `/domain-modeling` when decisions land. See `Docs/agents/domain.md`.

## Tickets

Work is tracked as GitHub issues (map #35 untuk upstream sync). One ticket = one session; close each session with a `/handoff`.
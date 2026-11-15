# SINEMAQ

A media streaming app for Android/iOS/TV, rebranded from [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) (Kotlin Multiplatform + Compose Multiplatform, GPLv3).

## Project facts

- **Upstream origin**: NuvioMedia/NuvioMobile (GPLv3). This fork keeps GPLv3 and full upstream attribution.
- **Brand**: SINEMAQ. Application ID `com.sinemaq.app` (debug `com.sinemaqdebug.com`), iOS bundle `com.sinemaq.media`.
- **Internal Kotlin packages stay `com.nuvio.app`** (deliberate — minimises rebrand risk). See ticket T1.
- **Build** (Android): `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/Contents/Home ./gradlew :androidApp:assembleFullDebug`
- **Submodules**: `MPVKit`, `libass-android` are git submodules, not checked out by default. Fetch before building.
- **Secrets/keys** live in `local.properties` (gitignored). Never commit real API keys.

## Agent skills

### Issue tracker

Issues live on GitHub at `Callmerev95/SINEMAQ`. See `Docs/agents/issue-tracker.md`.

### Triage labels

Custom vocabulary: five canonical state roles plus supporting category/priority/resolution labels. See `Docs/agents/triage-labels.md`.

### Domain docs

Single-context layout. `CONTEXT.md` and `docs/adr/` are created lazily by `/domain-modeling` when decisions land. See `Docs/agents/domain.md`.

## Rebrand tickets

Work is tracked as GitHub issues T1–T7 (see the repo issue list). One ticket = one session; close each session with a `/handoff`.
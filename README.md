# Wyrix News (Android)

A native Android news app (Kotlin, Jetpack Compose, Material 3) showing **real** headlines from
FreeNewsAPI.ai. No mock articles: if nothing is available the app says
*"No recent news available."*

## Build with GitHub Actions
1. Push this folder to a GitHub repository (branch `main`).
2. Open **Actions → Android CI**. It runs unit tests, lint and builds the **debug APK**.
3. Download `wyrix-debug-apk` from the run's artifacts and install it.

No API key or secret is needed. FreeNewsAPI.ai is keyless.

### Signed release bundle (optional)
Add these repository **secrets**: `KEYSTORE_BASE64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. The workflow then also builds `wyrix-release-aab`.
Optional repository **variable** `WYRIX_RSS_FEEDS` enables RSS fallback (see below).

### Local build
`gradle wrapper --gradle-version 8.9` once (the wrapper jar is not included), then
`./gradlew assembleDebug`. Needs JDK 17 and the Android SDK.

## Status: read this first
This code was written **without being compiled** (the authoring sandbox had no network, so Gradle
could not run). The first CI run is the first real build; expect to fix a few compile errors.
Paste the log back and they can be fixed quickly.

## Data provider (verified from official docs/terms)
FreeNewsAPI.ai `GET /v1/search`: keyless, commercial use allowed, caching allowed, must link back to
each article's original URL, 20 req/s per IP, **no SLA**, ~30-day window. Country is *inferred*, and
no category field is documented, so categories are keyword searches and some Indian publishers may
be tagged with another country. The app only shows headline, short excerpt, source and link.

Not verified: NewsData.io, Guardian, Mediastack and every publisher's RSS terms. GNews and
NewsAPI.org free plans forbid production/commercial use, so they are not included.

## Architecture
`domain/` query engine, dedupe, ranking, breaking policy · `data/remote` providers
(`NewsProvider` interface: FreeNews, RSS) + `ProviderGate` (cooldown/backoff) · `data/local` Room
cache + bookmarks · `data/repository` cache-first `NewsRepository` (one in-flight refresh per feed,
min refresh interval, max 3 concurrent calls, provider fallback, never wipes cache on failure) ·
`presentation/` ViewModels + Compose screens. Hand-written DI (`AppContainer`), no codegen besides Room.

Query note: the API requires **all** query words, so "Rajasthan politics" is sent, not
"Rajasthan politics latest news"; freshness comes from date sorting.

## Not implemented (on purpose)
- **Firebase push notifications**: need your own `google-services.json`.
- **AdMob consent form (UMP)**: required before real ads in the EEA/UK; not added because only test ads are on.
- **Deep links**: no domain is owned yet.
- Per-article category labels (the provider does not supply them).
- Instrumented tests (Room, Compose UI).
- Hindi translations (UI text, names, info pages) were written without a native-speaker review; have them checked before release.
- Before Play release: set `wyrix.targetSdk` to Google's current requirement, publish a privacy-policy URL, fill the Data safety form.

## Reader, thumbnails, ads
- **Reader:** "Read full article" opens the publisher's page in an in-app WebView (https only, no file access, external schemes blocked). "Open in browser" is still available.
- **Thumbnails:** the API has no images, so `ImageResolver` reads each shown article's `og:image` from the first 150 KB of its page, once per article, max 4 at a time, and saves the result. Some publishers block this, so a few cards stay text-only.
- **Ads:** one AdMob banner above the bottom bar on the main tabs, using Google's **test** ad units by default. For real ads set `ADMOB_APP_ID` and `ADMOB_BANNER_ID` at build time. Never click your own live ads.

## Hindi
- **App language:** Settings → App language (System / English / हिन्दी). Uses AppCompat per-app locales, so it also appears in Android 13+ system settings.
- **News language:** Settings → News language (English / हिन्दी / All). Hindi feeds send Hindi search words (राजनीति, राजस्थान ...) with `lang=hi`. Picking an app language also switches the news language to match.
- Hindi coverage depends on what the API has indexed; states and categories may be sparser than in English.

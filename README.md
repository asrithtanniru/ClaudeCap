# ClaudeCap

A personal Android home-screen widget showing Claude Pro usage: the 5-hour
session bar, the weekly bar, and when each resets. Sideloaded, personal use
only — see [Security and hygiene](#security-and-hygiene).

This relies on an **undocumented** claude.ai endpoint (there is no public API
for Pro usage), so it may break without warning whenever Anthropic changes
that page.

## Building

Requires JDK 17 and the Android SDK (platform 36, build-tools 36.x).

```
./gradlew assembleDebug      # debug build
./gradlew testDebugUnitTest  # unit tests
./gradlew lintDebug          # lint
./gradlew assembleRelease    # release build (R8 + resource shrinking)
```

## Signing for personal use

The release build is unsigned by default. Two options:

**Debug key (simplest, fine for personal sideloading):** install the debug
APK instead of the release one:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Local release keystore:** generate one and keep it out of git (it's already
covered by `.gitignore` — `*.jks`, `*.keystore`):

```
keytool -genkey -v -keystore ~/claude-usage-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias claude-usage
```

Then add a signing config to `app/build.gradle.kts` pointing at that keystore
(e.g. read the path/passwords from environment variables or a local,
git-ignored `keystore.properties` file — never commit credentials), and sign:

```
./gradlew assembleRelease
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore ~/claude-usage-release.jks \
    app/build/outputs/apk/release/app-release-unsigned.apk claude-usage
```

## Installing

```
adb install -r app/build/outputs/apk/release/app-release.apk
```

(or the debug APK, per above, if you skipped signing).

## Adding the widget

1. Open the app, tap **Sign in**, and log in to claude.ai in the WebView.
   - If Google SSO is blocked inside the WebView, long-press the page to
     paste a cookie header instead: in a desktop browser, open
     claude.ai → DevTools → Network, reload, click any request to claude.ai,
     and copy the `Cookie` request header value. Paste that plus the org id
     (the `lastActiveOrg` cookie's value) into the dialog.
2. Long-press your home screen → **Widgets** → **ClaudeCap**, and place it.
3. It auto-refreshes every 12 hours; tap the reload icon on the widget for
   an immediate refresh.

## Re-logging in when the session expires

The claude.ai session cookie lasts weeks but will eventually expire. When it
does, the widget and app show "Tap to sign in" / "Not signed in". Open the
app and repeat the **Sign in** step above — no need to remove and re-add the
widget.

## Security and hygiene

- Cookies and tokens are stored only in an Android Keystore–encrypted
  SharedPreferences store (`SecureStore`), never logged, and never sent
  anywhere except to claude.ai.
- `allowBackup` is disabled and all traffic is HTTPS-only.
- This app is for a single claude.ai account. If you share the APK with a
  friend, they sign in with their own account — don't share your session
  cookie.
- Since this depends on an undocumented endpoint, Anthropic can change it at
  any time and break the parser. If usage numbers look wrong, use
  **Show raw JSON** in the app to capture the current response shape.

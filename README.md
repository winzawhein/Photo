# Photo for Android — v1.5

Capacitor 8.6.0 Android project for https://antiquewhite-hornet-229089.hostingersite.com/.
Application ID: `com.photo.mobile`. Android 7.0+ (API 24), target API 36.

## Gallery selection update

The chooser uses Android Open Document with a Get Content fallback. Single-URI and ClipData gallery results are handled explicitly, duplicate URIs are removed, and the HTML multiple-selection setting is respected. MIME and extension accept filters are normalized. Read grants are retained when offered, without requesting broad photo-library access. AndroidX picker results are dispatched before Capacitor/Cordova routing. Cancel returns a null result to WebView so the chooser can be reopened. Device gallery selection and authenticated uploads still require phone testing.

## GPS update

The GPS button now connects WebView geolocation to Android foreground coarse/fine location permissions. Both are requested together for Android 12+; approximate access is accepted. Only the configured HTTPS origin is allowed. No background location permission is requested, and the app does not log coordinates. The existing website handles filling and submitting coordinates. Permission denial and disabled Location services offer settings links with explicit retry instructions.

After installing, tap Use current GPS location, choose Precise location if available, and allow while using the app. The phone must have Location enabled. GPS acquisition and signed-in submission still require device verification; no physical device is connected.

## Mobile layout update

Tables scroll inside their containers instead of moving the entire page. Row action menus expand within their cells so they are not clipped by the scroll container. Upload dialogs use a single flexible column, wrap help text, constrain native file inputs, and scroll vertically to the submit buttons. These mobile-only CSS overrides are bundled in the APK and injected on page load; the hosted website is not modified. They also apply to dynamically inserted dialogs.

Browser fixture checks using the shipped site CSS passed at 320, 360, 393, and 430 CSS pixels. Authenticated pages and actual uploads still need device testing.

## Included

- Live website with existing login and first-party cookie persistence.
- Full-height website without a native title header or bottom toolbar.
- Pull-to-refresh checks the actual touched DOM scroll containers. Normal downward scrolling inside the website is not intercepted.
- Refresh feedback appears only for a deliberate downward pull from the top; short, horizontal, multi-touch, and form-control gestures are excluded.
- Refresh spinner clears on page completion, visible page commit, errors, or a 20-second timeout.
- Loading progress, connection/server-error screen, and retry.
- Android back navigation and exit confirmation.
- System file picker for website upload inputs, including multiple selection when requested by the website.
- External HTTPS, phone and email links handed to installed apps.
- Website JavaScript alert/confirmation dialogs.
- Keyboard/system-bar insets, rotation handling, and WebView lifecycle cleanup.
- Teal leaf launcher icon and splash screen recreated as vectors from the supplied reference.
- Adaptive launcher icon with Android 7 fallback and readable system-bar icons.
- HTTPS-only website navigation, mixed-content blocking, no broad storage/camera permissions, disabled backups.

## Architecture

Capacitor hosts the Android activity and bundled local shell. MainActivity presents an isolated Android WebView for the remote website, with native controls. Remote website scripts do not receive the Capacitor native bridge. A narrowly scoped AndroidX web-message channel accepts only refresh requests from the configured HTTPS origin and the main frame. Pull-to-refresh requires a WebView with WEB_MESSAGE_LISTENER support; older unsupported WebViews retain normal scrolling. This avoids relying on Capacitor's development-oriented `server.url` configuration and does not require changing the website's server code.

This is an online website wrapper, not an offline copy or a native rewrite. Website changes appear automatically. The backend must remain available. Website authentication/session expiry rules still apply. No credentials are embedded.

## Build

Install Node 22+, JDK 21, Android SDK 36 and Android build tools. Set JAVA_HOME and ANDROID_HOME for your machine.

```sh
npm ci
npx cap sync android
cd android
./gradlew assembleDebug lintDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
For Windows, use `gradlew.bat`. Android Studio can open the `android` folder.

The supplied APK uses Android's development signing key and is for testing/direct installation. For public distribution, create and securely retain your own release signing key, configure release signing in Android Studio, and build a signed release APK or AAB. Never commit your release signing credentials.

## Regression checks

Run `node tests/pull-refresh.test.cjs` for gesture logic regression checks (nested scrollers, document scrolling, top refresh, duplicate prevention, reset, gesture cancellation and form controls). These are synthetic event tests, not physical-device tests.

## Limitations and device testing

The public site redirects to login and does not allow public registration. No account was supplied; authenticated uploads, downloads and account flows need testing on a real device. An emulator launch was attempted but blocked by the host sandbox's process restrictions. A successful build is not a device runtime test.

Downloads open in the external browser, which may require a separate login. Blob/data downloads and direct camera capture are not implemented. Push notifications, biometric login, background uploads, and verified App Links require additional backend/product configuration and are not included. External OAuth may require a deliberate browser callback integration.

Test before distribution:
1. Launch, sign in, close/reopen and confirm session behavior.
2. Browse multiple pages and use Android Back.
3. Select one and multiple photos in the site's upload controls.
4. Disable connectivity, reload, restore connectivity and retry.
5. Rotate, open the keyboard, and check forms on a small screen.
6. Verify external links, download behavior, pull-to-refresh and logout.

Runtime npm dependencies reported no known vulnerabilities during the build. npm reports four moderate findings in transitive CLI development tooling; these are not bundled into the APK. Do not use npm audit's suggested downgrade to Capacitor 4.

# Google Drive sign-in — one-time setup

Jupiter's Cloud Hub signs each user into **their own** Google account (the normal
Google account chooser) and then talks to Google Drive. Google requires every app
that does this to be registered with an **OAuth client** that is keyed to the app's
package name **and** its signing certificate (SHA‑1). That registration can only be
created from *your* Google account, so it is the one step you do once; after that,
every user just taps **Connect**, picks their account, and it works.

This is a ~5 minute, one-time setup.

---

## What Jupiter already ships

- A **fixed debug signing keystore** (`app/keystore/jupiter-debug.keystore`) so the
  signing certificate — and therefore its SHA‑1 — is **stable** across every build
  (local and CI). Without this, each machine would sign with a different certificate
  and Google sign-in would break.
- All the sign-in code (Credential Manager account chooser → Drive authorization →
  account email + storage quota), gated behind one build config value:
  `BuildConfig.GDRIVE_WEB_CLIENT_ID`. While it is empty, **Connect** shows a
  "set up Google Drive" notice instead of attempting sign-in.

## The two values you register

| Field | Value |
|---|---|
| **Package name** (installed debug build) | `com.jupiter.filemanager.debug` |
| **Signing SHA‑1** (fixed debug keystore) | `93:BE:BC:7D:F7:BB:AC:69:F3:1E:C7:91:C7:EF:AE:84:FF:FE:17:9B` |

> Re-derive the SHA‑1 any time with:
> `keytool -list -v -keystore app/keystore/jupiter-debug.keystore -storepass android -alias androiddebugkey`

---

## Steps (Google Cloud Console)

1. Go to <https://console.cloud.google.com/> and create (or pick) a project.
2. **APIs & Services → Library →** enable the **Google Drive API**.
3. **APIs & Services → OAuth consent screen:** configure it (External is fine for
   testing). Add your Google account as a **Test user** so you can sign in before the
   app is verified. Add the scope `…/auth/drive.file` (app-created/opened files —
   needs **no** Google verification). `drive.readonly`/`drive` see *all* files and are
   "restricted" scopes that require Google's verification before public release.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID:**
   - **Android** client → package name `com.jupiter.filemanager.debug`, SHA‑1 from the
     table above. (This authorizes the app binary.)
   - **Web application** client → no redirect URIs needed. **Copy its Client ID** — this
     is the `serverClientId` the app uses.
5. Put the **Web** client ID into the build as a Gradle property — pick one:
   - In `~/.gradle/gradle.properties` (recommended, keeps it out of the repo):
     ```
     JUPITER_GDRIVE_WEB_CLIENT_ID=1234567890-xxxxxxxxxxxxxxxx.apps.googleusercontent.com
     ```
   - or pass it to the build: `./gradlew assembleDebug -PJUPITER_GDRIVE_WEB_CLIENT_ID=...`
   - For the GitHub Actions build, add it as a repository **secret/variable** and pass
     `-P…` in the workflow (ask me and I'll wire it).
6. Rebuild. **Cloud Hub → Google Drive → Connect** now opens the Google account
   chooser; after consent the card shows your email and Drive storage usage.

---

## Notes

- The release build (`app-release.apk`, R8) has applicationId `com.jupiter.filemanager`
  (no `.debug`) but is signed with the **same** keystore, so its SHA‑1 is identical.
  If you distribute the release build, add a **second Android OAuth client** for package
  `com.jupiter.filemanager` with the same SHA‑1.
- The Drive **access token is stored encrypted** (EncryptedSharedPreferences) and is
  never logged.
- This first version connects the account and shows quota. Browsing/transferring Drive
  files inside the file manager is the next step (the Drive REST client is the same
  OkHttp path used for WebDAV).
- `drive.file` scope is the no-verification-needed default. Switch to `drive.readonly`
  only if you need to see files the app didn't create, and budget for Google's OAuth
  verification.

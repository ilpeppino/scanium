# Google Play Internal Testing Guide

This guide describes how to prepare and deploy Scanium to the Google Play Store for internal testing.

## 1. Prerequisites

- Google Play Console account with developer access to the Scanium project.
- Release signing key (kept secure and NOT in version control).
- `local.properties` configured with signing secrets (for local release builds).

## 2. Signing Configuration

To build a signed release locally, add the following to your `local.properties`:

```properties
scanium.keystore.file=/path/to/your/release.keystore
scanium.keystore.password=your_keystore_password
scanium.key.alias=your_key_alias
scanium.key.password=your_key_password
```

**WARNING:** Never commit your keystore file or passwords to the repository.

## 3. Building the App Bundle (AAB)

Google Play requires the Android App Bundle format for new applications.

To generate a release AAB:

```bash
./gradlew bundleRelease
```

The output will be located at:
`androidApp/build/outputs/bundle/release/androidApp-release.aab`

## 4. Versioning

Before building, ensure the version code and name are bumped if necessary.
See [VERSIONING.md](VERSIONING.md) for the strategy.

You can set versions via environment variables for CI:
- `SCANIUM_VERSION_CODE`
- `SCANIUM_VERSION_NAME`

## 5. Deployment to Play Console

1. Log in to [Google Play Console](https://play.google.com/console).
2. Select **Scanium**.
3. Go to **Testing > Internal testing**.
4. Create a new release.
5. Upload the `.aab` file.
6. Provide release notes.
7. Review and roll out to internal testing.

## 6. Managing Testers

1. In the Internal testing section, go to the **Testers** tab.
2. Create or select a mailing list of testers.
3. Share the **Join on the web** or **Join on Android** link with your testers.

## 7. Smoke Test Checklist

After installing from the Play Store, verify the following:

- [ ] **Scan Mode:** Continuous scanning works smoothly.
- [ ] **Object Detection:** Items are detected and aggregated.
- [ ] **Cloud Classification:** If enabled, enhanced labels appear after stabilization.
- [ ] **Drafting:** Can create an eBay listing draft from a scanned item.
- [ ] **Copy/Share:** Posting Assist works and copies data to clipboard.
- [ ] **Diagnostics:** Crash reporting (Sentry) initializes if opted-in.
- [ ] **Data Safety:** Compliance screens (Privacy, Terms, About) are accessible.

# Product Overview

## What the app does now
- Live camera scanning with on-device ML Kit for object, barcode/QR, and document OCR modes (API 24+).
- Shows detection overlays (boxes + labels) and aggregates stable items with confidence and thumbnail snapshots.
- Maintains an item list; users can tap through scan sessions without losing prior detections.
- Export-first flow: select items and share CSV/ZIP exports; marketplace integrations are temporarily disabled.
- Domain pack config provides finer-grained categories beyond ML Kit defaults.
- Export Assistant chat helps draft listings with suggestions and quick actions.
- Paywall + billing flow gates advanced assistant features and cloud classification entitlements.

## User flows & screens

### Primary Screens
- **Home/Camera**: `CameraScreen` hosts preview, overlays, and scan mode switching.
- **Items list**: `ItemsListScreen` displays aggregated items from `ItemsViewModel`.
- **Export**: `ItemsListScreen` lets users select items and export CSV/ZIP bundles for sharing.
- **Assistant**: `AssistantScreen` provides an export-focused chat experience for selected items.
- **Paywall/Billing**: `PaywallScreen` presents subscription details and purchase/restore actions.

### Selling Flow (temporarily disabled)
- **Sell on eBay**: `SellOnEbayScreen` - marketplace selection and listing initiation.
- **Draft Review**: `DraftReviewScreen` - review and edit listing drafts.
- **Posting Assist**: `PostingAssistScreen` - AI-assisted listing optimization.

### Settings (6-category IA)
- **Settings Home**: `SettingsHomeScreen` - navigation hub for all settings categories.
- **General**: `SettingsGeneralScreen` - app-wide preferences.
- **Camera & Scanning**: `SettingsCameraScreen` - camera and detection preferences.
- **AI Assistant**: `SettingsAssistantScreen` - assistant behavior and API settings.
- **Notifications & Feedback**: `SettingsFeedbackScreen` - notification and feedback preferences.
- **Data & Privacy**: `SettingsPrivacyScreen` - privacy controls and data management.
- **Developer Options**: `DeveloperOptionsScreen` - debug tools, diagnostics, and system health (debug builds only).

### Legal & Info Screens
- **Data Usage**: `DataUsageScreen` - data usage disclosure.
- **Privacy Policy**: `PrivacyPolicyScreen` - privacy policy display.
- **Terms**: `TermsScreen` - terms of service.
- **About**: `AboutScreen` - app info and version.

**Total Routes:** 18 defined in `NavGraph.kt`.

## Feature flags / modes
- Scan modes: object, barcode/QR, document (switchable in camera UI).
- Cloud classification pipeline lives in `ml/classification/CloudClassifier.kt` and activates only when `SCANIUM_API_BASE_URL`/`SCANIUM_API_KEY` BuildConfig values are provided; otherwise on-device labels are used.
- No other runtime feature-flag system is present; changes are code-driven.

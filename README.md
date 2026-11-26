# MLTheta Mobile (Android Shell)

This repository contains the native Android application shell, Capacitor runtime, and CI/CD pipelines for **MLTheta**.

The core textbook content, MDX interactive lessons, and React web application are maintained in the main repository: [**mltheta/mltheta**](https://github.com/mltheta/mltheta).

---

## Architecture & How It Works

1. **Static Web Bundle**: Next.js exports static HTML, CSS, JavaScript, and data assets into an `out/` bundle.
2. **Capacitor Android Container**: Capacitor wraps the static bundle inside an Android WebView with native plugins (`@capacitor/network`, `@capacitor/preferences`, `@capacitor/share`, etc.) for offline access and native device features.
3. **Automated CI/CD**: When a new release tag (e.g. `v2.1.0`) is published on `mltheta/mltheta`, GitHub Actions in this repo fetches the release's `web-build.zip`, syncs it to Android assets, compiles the signed release APK, and publishes it.

---

## Local Development Quickstart

### 1. Prerequisites
- Node.js 18+
- Java JDK 17 or 21
- Android Studio / Android SDK (command line tools & build-tools)

### 2. Setup
```bash
git clone https://github.com/mltheta/mltheta-mobile.git
cd mltheta-mobile
npm install
```

### 3. Sync from Local Web Build
If you have the web repo cloned side-by-side (`../mltheta`):
```bash
# 1. Build the web app in the web repo
cd ../mltheta && npm run build

# 2. Sync to mobile and prune heavy binary assets
cd ../mltheta-mobile && npm run sync:local
```

### 4. Build Signed Release APK
```bash
npm run build:apk
# Output APK will be at:
# android/app/build/outputs/apk/release/app-release.apk
```

### 5. Open in Android Studio
```bash
npm run open:android
```

---

## Repository Scripts

| Command | Description |
| :--- | :--- |
| `npm run sync:local` | Copies web build from `../mltheta/out` -> `./out` and updates Android assets |
| `npm run sync:release` | Downloads latest `web-build.zip` from `mltheta/mltheta` GitHub releases |
| `npm run cap:sync` | Syncs Capacitor native configuration and plugins |
| `npm run prune:android` | Prunes heavy non-essential offline binaries to keep APK size minimal (<15 MB) |
| `npm run build:apk` | Runs Gradle to assemble the signed release APK |
| `npm run build:bundle` | Runs Gradle to assemble the Google Play `.aab` bundle |
| `npm run open:android` | Launches the project in Android Studio |

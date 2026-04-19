# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Run unit tests
./gradlew connectedDebugAndroidTest  # Run instrumented tests (requires device/emulator)
```

## Architecture

Android app that runs a Ktor/Netty HTTPS server on the device, exposing MLKit OCR and barcode scanning over a REST API. The server runs inside an Android foreground service (`OCRService`) on port 8443.

**Core flow:** `MainActivity` starts `OCRService` → service launches Ktor HTTPS server → external clients hit `/api/v1/ocr` with an image → MLKit processes it concurrently (text recognition + barcode scanning) → results returned as JSON.

**Key files:**
- `app/src/main/java/it/denv/ocr/server/OCRService.kt` — Ktor server setup, HTTPS config, API routes, MLKit processing
- `app/src/main/java/it/denv/ocr/server/responses/OcrResult.kt` — Response models and `OcrResultProcessor` that maps raw MLKit objects to serializable types
- `app/src/main/java/it/denv/ocr/server/responses/BatteryStatus.kt` — Battery status response model

**API endpoints:**
- `GET /healthz` — health check (plain text "OK")
- `POST /api/v1/ocr` — OCR + barcode scanning (accepts binary image)
- `GET /api/v1/battery` — device battery level and charging state

**HTTPS setup:** Uses BouncyCastle as security provider. A PKCS12 keystore is generated at runtime and stored in the app's internal files directory (`filesDir/keystore.p12`). The keystore password is randomly generated and persisted in SharedPreferences.

**Serialization:** Kotlin serialization with `@Serializable` annotations and `kotlinx-serialization-json`.

## Tech Stack

- Kotlin 2.0.21, Android SDK 35 (minSdk 33), Gradle 8.11.1, AGP 8.7.3
- Ktor 2.3.12 (Netty server), MLKit (text-recognition + barcode-scanning), BouncyCastle (bcprov-jdk18on + bcpkix-jdk18on), kotlinx-serialization 1.7.3

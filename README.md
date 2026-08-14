# Warsaw Live Transit KMP

A full-stack, cross-platform spatial application built with **Kotlin Multiplatform** and **Compose Multiplatform**. The system tracks and visualizes Warsaw's public transport fleet (trams and buses) in real-time, leveraging official GTFS/API streams from the City of Warsaw (`api.um.warszawa.pl`).

---

## System Architecture & Features

This project showcases a modern **Full-Stack Kotlin** approach, running the same core business logic across **Android, iOS, Desktop, Web (Wasm/JS), and Server**:

* **Backend (Ktor Server):** Fetches live telemetry data from Warsaw Open Data endpoints, handles caching/rate-limiting, and streams processed spatial entities to client applications.
* **Shared Core (`:core` & `:app:shared`):** Contains common domain models, spatial calculations, state management, and API DTOs shared seamlessly across all targets.
* **Multiplatform UI (Compose Multiplatform & SwiftUI):** Declarative cross-platform UI rendering real-time vehicle positions on interactive maps.
* **Security & Environment Config:** Strict isolation of sensitive credentials (API keys, backend IPs) via local environment properties.

---

##  Tech Stack

* **Language:** Kotlin 2.x
* **UI Framework:** Compose Multiplatform (Android, Desktop, Web, iOS entry) + SwiftUI integration
* **Server:** Ktor Framework
* **Asynchronous & Reactive Data:** Kotlin Coroutines & Flows
* **Build System:** Gradle (Multi-module setup)
* **Targets:** Android, iOS, Desktop (JVM), Web (WasmJs / JS), Ktor Backend

---

##  Environment Setup & Security

Before running the project, you need to configure your local credentials and backend connection.

### 1. Warsaw Open API Key
Live transit data requires an access token from the official portal:
1. Register at [api.um.warszawa.pl](https://api.um.warszawa.pl).
2. Obtain your personal API Key.
3. Add the following line to your `local.properties` file in the project root:
   ```properties
   WAW_API_KEY=your_actual_api_key_here

### 2. Server IP Configuration

For mobile devices or emulators to communicate with the local Ktor server:
1. Ensure both the backend and client target are connected to the same local Wi-Fi network.
2. Update SERVER_IP in your local properties if testing on physical hardware.
   🚀 Running the Applications
   Backend Server

Run the Ktor backend first to start ingestion and proxying of live transport data:
Bash

./gradlew :server:run

Client Applications

    Android App:
    ./gradlew :app:androidApp:assembleDebug

    Web App:
        Wasm Target (Modern Browsers):
        ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
        JS Target (Legacy Browsers):
        ./gradlew :app:webApp:jsBrowserDevelopmentRun

Built as a personal project demonstrating Kotlin Multiplatform capability in real-time geospatial data tracking.
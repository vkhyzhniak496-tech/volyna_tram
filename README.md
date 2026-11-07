# 🚋 Volyna Tram (Kotlin Multiplatform / Real-Time GIS PoC)

> **Project Status: Archived / Experimental Proof-of-Concept (Tech Spike)**  
> This repository is a research exploration of **Kotlin Multiplatform (KMP)** and **Compose Multiplatform Canvas (Skia / WebGL via Wasm)** for processing and rendering streaming geospatial data.  
> **This project is not an out-of-the-box turnkey consumer application.** The codebase serves architectural research and benchmarking purposes only.

---

## 🔬 Research Scope & Objectives

The goal of this experiment was to test the limits of Compose Canvas without relying on third-party web map engines (such as Leaflet, Mapbox, or MapLibre), verifying a **Smart Server / Lightweight Client** architecture across multiple platforms (Android and Web via Compose Wasm):

1. **Native Canvas Rendering:** Direct spherical projection of WGS84 geographic coordinates onto a virtual screen plane using the Compose `Canvas` API, evaluating dynamic Level of Detail (LOD) and hardware-accelerated drawing.
2. **Server-Side Spatial Workload:** Offloading spatial processing to a dedicated Ktor backend—including track snapping, in-memory vehicle indexing, instantaneous speed computation ($v = \Delta d / \Delta t$ via Haversine), and GeoJSON transformations.
3. **Cross-Platform Shared Models:** Sharing domain models, serializers, and geospatial data contracts across mobile and browser targets using Kotlin Multiplatform.

---

## 🏛️ System Architecture

[Warsaw Public Transport API]       [OpenStreetMap / Overpass API]
│                                   │
│ (Live polling)                    │ (BBOX GeoJSON query)
▼                                   ▼
┌─────────────────────────────────────────────────────────────┐
│                 Ktor Server Core Engine                     │
│  - Background polling worker & in-memory registry           │
│  - Haversine velocity computation (Δd / Δt)                 │
│  - Static network cache (tracks, platforms, stops)          │
└──────────────┬──────────────────────────────┬───────────────┘
│                              │
│ /api/network/map             │ /api/trams/live
▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│           Compose Multiplatform Client Layer                │
│  - Desktop, Android (Skia/Vulkan) & Web (Wasm/WebGL)        │
│  - Custom coordinate projection (MapProjection)             │
│  - Decoupled layers: InfrastructureCanvas & TramMarkers     │
└─────────────────────────────────────────────────────────────┘


---

## ⚠️ Environmental Constraints & Disclaimer

This codebase represents an experimental technical spike. Setting up the application locally requires understanding several platform-specific caveats:

* **Local Infrastructure Dependency:** The client is designed around a dedicated backend service. Without an active Ktor server providing `/api/network/map` and `/api/trams/live`, client targets run in an uninitialized state.
* **Browser Security & Wasm Constraints:** The Web target (Compose Wasm) runs inside browser sandboxes and is subject to Cross-Origin Resource Sharing (CORS) enforcement and fetch API loopback restrictions. Accessing a local Ktor server requires proper CORS header negotiation on the server.
* **Coroutine Error Boundaries:** In-flight network exceptions inside Compose `LaunchedEffect` coroutines must be handled explicitly; unhandled network fetch failures will interrupt the Wasm frame clock.
* **Build Tooling Volatility:** The multiplatform Gradle setup reflects specific experimental versions of Compose Multiplatform and Wasm toolchains and is not maintained for newer toolchain revisions.

---

## 🛠️ Technology Stack

* **Core:** Kotlin Multiplatform (KMP)
* **Backend:** Ktor Server (Netty engine), `kotlinx.coroutines`, `kotlinx.serialization`
* **Presentation:** Compose Multiplatform (Compose Canvas, Skia, WebAssembly)
* **Networking:** Ktor Client (CIO & JS Fetch engines)
* **Data Interchange:** GeoJSON (FeatureCollection, LineString, Point)
* **Targets Explored:** Android, Web (Wasm/JS), Desktop (JVM)
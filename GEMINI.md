# GYM QR Displayer

## Project Overview
**GYM QR Displayer** is an Android application with a companion Wear OS app designed to fetch and display a GYM QR code for gym entry. The project is built using Kotlin and Jetpack Compose for both the mobile and wearable platforms.

### Key Functionalities
- **Mobile App (`app/`)**: Handles user authentication, displays the QR code on the mobile device, provides home screen widgets (using Jetpack Glance) for quick access, and synchronizes credentials and data to the Wear OS device via the Google Play Services Wearable Data Layer.
- **Wear OS App (`wear/`)**: Displays the QR code directly on a smartwatch, includes a Wear Tile (`GymTileService`) for fast access without opening the app, and receives synchronized credentials from the mobile app.

### Main Technologies & Libraries
- **Language**: Kotlin (Java 11 target)
- **UI Frameworks**: Jetpack Compose, Wear OS Compose, Horologist, Jetpack Glance (App Widgets), Wear Tiles
- **Network & Parsing**: OkHttp, Jsoup (used for parsing HTML/responses to fetch the QR)
- **QR Code Generation**: ZXing (Zebra Crossing)
- **Data Storage & Security**: Jetpack DataStore Preferences, AndroidX Security Crypto (for secure credential storage)
- **Concurrency**: Kotlin Coroutines & Flow
- **Wearable Communication**: Google Play Services Wearable API (Data/MessageClient)

## Building and Running
The project is built using Gradle Kotlin DSL (`build.gradle.kts`).

- **Build the entire project**:
  ```bash
  ./gradlew build
  ```
- **Install and run the mobile app**:
  ```bash
  ./gradlew :app:installDebug
  ```
- **Install and run the Wear OS app**:
  ```bash
  ./gradlew :wear:installDebug
  ```
- **Run Unit Tests**:
  ```bash
  ./gradlew test
  ```

## Development Conventions & Architecture
- **UI Architecture**: Strongly relies on the **MVVM (Model-View-ViewModel)** pattern. ViewModels (e.g., `WearViewModel`) manage UI state using Kotlin `StateFlow` and sealed classes (e.g., `UiState.Loading`, `UiState.Success`, `UiState.Error`).
- **Declarative UI**: All UI components are built using Jetpack Compose (`@Composable` functions).
- **Asynchronous Operations**: Network requests, data storage interactions, and QR code generation are handled asynchronously using Coroutines (`Dispatchers.IO`, `Dispatchers.Default`) to keep the main UI thread responsive.
- **Data Privacy**: Sensitive data like user credentials are saved securely using Android's encrypted storage mechanisms.
- **Wear OS Considerations**: Follows Wear OS design guidelines, including specific handling for ambient modes, rounded screens, and providing lightweight experiences via Wear Tiles.
# 🍽 Calorie Estimator - Android Frontend

A modern Android application built with Jetpack Compose that estimates calories and nutrients from food photos. This app serves as the frontend for a FastAPI-based AI pipeline that performs segmentation, classification, and volume estimation.

## 🚀 Features

- **Dual-View Image Upload**: Specifically designed to take/upload Top-View and Side-View images for accurate volume estimation.
- **Real-time Processing**: Communicates with a backend pipeline to trigger food segmentation and analysis.
- **Comprehensive Nutrition Report**: Shows a detailed breakdown of:
  - **Meal Totals**: Aggregate nutritional data for the entire meal.
  - **Individual Food Breakdown**: Detailed reports for each specific item found (e.g., "Porota", "Yogurt").
  - **Metrics**: Calories (kcal), Volume (cm³), Macros (Carbs, Protein, Fat, Fiber), Minerals (Sodium, Calcium, Iron), and Vitamins (A, C, D).
  - **Macro Split %**: Visualizing the percentage distribution of energy from Carbs, Protein, and Fat.
- **Modern UI/UX**: Built using Material 3 with a clean, responsive design.

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: Retrofit & OkHttp
- **Image Loading**: Coil
- **Architecture**: MVVM (ViewModel, State Management)
- **JSON Parsing**: GSON

## 📁 Project Structure (Frontend)

```text
app/src/main/java/com/example/demo/
├── MainActivity.kt          # Main UI implementation (Compose)
├── CalorieViewModel.kt      # State management and API logic
├── CalorieApiService.kt     # Retrofit interface for endpoints
└── CalorieData.kt           # Data models for API responses
```

## ⚙️ Backend Integration

The app is designed to work with a FastAPI backend. By default, it connects to `http://10.0.2.2:8000/` (Localhost for Android Emulator).

### API Endpoints Used:
- `POST /upload/top`: Uploads top-view image.
- `POST /upload/side`: Uploads side-view image.
- `POST /process`: Triggers the AI analysis pipeline (Segmentation -> Thumbnailing -> Classification -> Volume -> Nutrition).
- `GET /result/segmentation/top/content/{filename}`: Retrieves raw segmentation mask data.
- `GET /result/classification/top/content/{category}/{filename}`: Retrieves categorized food item data.

## 🏃 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/calorie-estimator-android.git
    ```
2.  **Open in Android Studio**:
    Open the project and wait for Gradle sync to complete.
3.  **Configure Backend URL**:
    In `CalorieViewModel.kt`, update the `baseUrl` if your backend is hosted elsewhere (e.g., local tunnel or public IP).
4.  **Network Configuration**:
    The app is pre-configured with `network_security_config.xml` to allow cleartext (HTTP) communication for local development.

## 🤝 Contributing

This project is part of a collaborative effort. Backend logic is handled by the [Calorie Estimator AI Service].

---
Developed with ❤️ by the Calorie Estimator Team.

# 🍽 Calorie Estimator - Android Frontend

A modern Android application built with Jetpack Compose that estimates calories and nutrients from food photos. This app serves as the frontend for a FastAPI-based AI pipeline that performs segmentation, classification, and volume estimation.

## 🚀 Features

- **Dual-View Image Upload**: Specifically designed to take/upload Top-View and Side-View images for accurate volume estimation.
- **Real-time Processing**: Communicates with a backend pipeline to trigger food segmentation and analysis.
- **Numpy Data Visualization**: Displays intermediate processed data (Numpy arrays) returned from the backend.
- **Full Nutrition Report**: Shows a detailed breakdown of:
  - Calories (kcal)
  - Macronutrients (Carbs, Protein, Fat, Fiber)
  - Micronutrients (Sodium, Calcium, Iron)
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
- `POST /process`: Triggers the AI analysis pipeline.
- `GET /result/classification/top`: Retrieves categorized classification data for the top view.
- `GET /result/classification/side`: Retrieves categorized classification data for the side view.

## 🏃 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/calorie-estimator-android.git
    ```
2.  **Open in Android Studio**:
    Open the project and wait for Gradle sync to complete.
3.  **Configure Backend URL**:
    In `CalorieViewModel.kt`, update the `baseUrl` if your backend is hosted elsewhere:
    ```kotlin
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://your-backend-ip:8000/") 
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    ```
4.  **Run the App**:
    Deploy to an emulator or physical device. Ensure your backend is running and accessible.

## 🤝 Contributing

This project is part of a collaborative effort. Backend logic is handled by the [Calorie Estimator API](link-to-backend-repo).

---
Developed with ❤️ by the Calorie Estimator Team.

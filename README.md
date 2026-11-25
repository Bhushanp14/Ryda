# RYDA Motorcycle Ride-Tracking and Community Platform (V1)

Welcome to the **RYDA** motorcycle ride-tracking, Django backend, and community platform repository!

## Project Structure

- **`android-v1/`**: The modern Jetpack Compose Android application (featuring AGP 9, Kotlin, Room/SQLite offline storage, foreground service GPS tracking, digital garage, community feed, and clubs/rallies).
- **`backend/`**: Django REST Framework backend API supporting PostgreSQL/SQLite, authentication, garage management, ride telemetry ingestion, community feed, and clubs/events.
- **`stitch_ryda_motorcycle_app_prototype/`**: UI/UX prototype designs and architecture specification.

---

## How to Open Android App in Android Studio

1. Launch **Android Studio**.
2. Select **Open** (or `File -> Open`).
3. Select the folder:
   ```
   E:\Ryda\android-v1
   ```
4. Android Studio will automatically load the Gradle project, sync dependencies, and build the index.
5. Select an Android Virtual Device (Emulator) or connect your physical Android phone (with USB debugging enabled).
6. Click **Run 'app'** (or press `Shift + F10`).

---

## Running the Backend (Django)

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Run migrations and start server:
   ```bash
   python manage.py migrate
   python manage.py runserver 0.0.0.0:8000
   ```

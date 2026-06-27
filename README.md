# Battery Protector

A simple and efficient Android application designed to extend your device's battery lifespan by preventing overcharging.

## Why use this app?
Lithium-ion batteries degrade faster when kept at 100% charge for long periods. Experts recommend keeping your battery between **20% and 80%** to maximize its health. This app acts as your personal monitor, alerting you exactly when your phone reaches your desired limit so you can unplug it.

## Key Features
- **Real-Time Monitoring:** Accurate, second-by-second battery percentage updates.
- **Customizable Limits:** Set your target charge level anywhere from 1% to 100%.
- **Custom Alarms:** Pick any ringtone or music file from your device to play when the limit is hit.
- **Background Protection:** Runs as a Foreground Service to ensure you never miss an alert, even if the app is closed.
- **Quick Access:** Open the app directly from the persistent notification in your status bar.

## How to Install
1. Clone this repository.
2. Open the project in **Android Studio**.
3. Build and run on your Android device (API 26+).

## Permissions Used
- `FOREGROUND_SERVICE`: To monitor battery in the background.
- `POST_NOTIFICATIONS`: To show status and alerts on Android 13+.

---
Developed with to save your battery.
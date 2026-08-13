# pedometer_pro example

[中文文档](README.md)

Demonstrates step count, live streams, and the Android sensor fallback when Google Play Services is unavailable. On phones with Google Play, the example loads about 10 days of Recording API history (including per-day totals). iOS uses about 7 days.

```sh
cd example
flutter pub get
flutter run
```

Android requires Activity Recognition permission. iOS requires Motion & Fitness permission.

The iOS example uses **Swift Package Manager** (no Podfile). The plugin still ships both CocoaPods (`ios/pedometer_pro.podspec`) and SPM (`ios/pedometer_pro/Package.swift`).

Android release signing uses the committed test keystore under [jks/](jks/README.md). **For the example app only — not for production.**

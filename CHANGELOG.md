## 0.1.0

* iOS supports both CocoaPods and Swift Package Manager. The example app uses Swift Package Manager.
* Renamed the package to `pedometer_pro` (Dart, Android, and iOS).
* Android `getStepCount` now falls back to `TYPE_STEP_COUNTER` when Google Play Services / Recording API is unavailable (Xiaomi, OPPO, vivo, and other devices without GMS).
* Android `getStepCount` reads Health Connect first (Samsung Health and other linked apps). `play-services-fitness` Local Recording is only a fallback and does not contain OEM health history.
* Added an example app.
* Removed donation links from the documentation.
* Pub.dev listing: https://pub.dev/packages/pedometer_pro
* Repository: https://github.com/whevether/pedometer_2

See [CHANGELOG_zh.md](CHANGELOG_zh.md) for Chinese.

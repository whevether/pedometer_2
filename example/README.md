# pedometer_pro 示例

[English](README_en.md)

演示步数查询、实时流，以及 Android 在无 Google Play 服务时的传感器回退。有 Google Play 的机型会查询 Recording API 的近 10 天历史（含按日明细）；iOS 为近 7 天。

```sh
cd example
flutter pub get
flutter run
```

Android 需授予活动识别权限；iOS 需授予运动与健身权限。

iOS 示例工程使用 **Swift Package Manager**（无 Podfile）。插件本身仍保留 CocoaPods（`ios/pedometer_pro.podspec`）与 SPM（`ios/pedometer_pro/Package.swift`）双模。

Android release 签名使用仓库内测试证书，见 [jks/README.md](jks/README.md)。**仅供示例打包，不可用于正式发布。**

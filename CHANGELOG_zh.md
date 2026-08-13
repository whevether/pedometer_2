## 0.1.0

* iOS 同时支持 CocoaPods 与 Swift Package Manager。示例应用使用 Swift Package Manager。
* 包更名为 `pedometer_pro`（Dart、Android、iOS）。
* Android 在 Google Play 服务 / Recording API 不可用时，`getStepCount` 回退到 `TYPE_STEP_COUNTER`（小米、OPPO、vivo 等无 GMS 机型）。
* Android `getStepCount` 优先读 Health Connect（三星健康等）。`play-services-fitness` Local Recording 只是回退，不含厂商健康 App 历史。
* 新增 example 示例应用。
* 文档去掉打赏链接。
* pub.dev：https://pub.dev/packages/pedometer_pro
* 仓库：https://github.com/whevether/pedometer_2

英文版见 [CHANGELOG.md](CHANGELOG.md)。

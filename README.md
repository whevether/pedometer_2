# Pedometer Pro

[中文文档](README_zh.md)

*Based on Pedometer & Pedometer_plus (references at the bottom)*

This plugin allows you to get this info on both Android and iOS:
- Get step count for a `from:to` date range
- Get step count since last system boot
- Get real-time step count (Stream)
- Get real-time pedestrian status: Walking, Stopped (Stream)
- Get real-time step count since a date (Stream) (iOS only; Android alternative below)

> Supported on both Android and iOS.
> Uses the Sensors API (Android & iOS) and the Recording API (Android, when Google Play Services is available).

Package: [https://pub.dev/packages/pedometer_pro](https://pub.dev/packages/pedometer_pro)

Repository: [https://github.com/whevether/pedometer_2](https://github.com/whevether/pedometer_2)

<img height="500px" src="assets/example_preview.png"/>

## Android data sources

| Capability | With Google Play Services | Without GMS (Xiaomi / OPPO / vivo, etc.) |
| --- | --- | --- |
| Real-time `stepCountStream` / `pedestrianStatusStream` | Hardware `TYPE_STEP_COUNTER` / `TYPE_STEP_DETECTOR` | Same sensors — **supported** |
| `getStepCount(from, to)` history | Recording API (~10 days) | Falls back to `TYPE_STEP_COUNTER` (**steps since last boot only**, cannot split by calendar day) |

On aggressive OEM battery savers (Xiaomi / OPPO / vivo), background listeners may be killed. Use the streams in the foreground, and ask the user to disable battery optimization for the app if you need more reliable tracking.

Run the sample app:

```sh
cd example && flutter run
```

Android release signing for the example uses a committed test keystore (`example/jks/`). See [example/jks/README.md](example/jks/README.md). Do not use it for production.

## Configuration

### Permissions

For both Android and iOS you need to request permission to track the user's activity.
I recommend using [permission_handler](https://pub.dev/packages/permission_handler), but you can use others if it suits you better.

<details open>
  <summary><b>iOS</b></summary>

This plugin supports **both CocoaPods and Swift Package Manager**. The example app uses Swift Package Manager.

1. In your `Info.plist`, located under `ios/Runner`, add this:

   ```xml
   <key>NSMotionUsageDescription</key>
   <string>This application tracks your steps</string>
   ```

   With Swift Package Manager and recent `permission_handler`, this Info.plist key is enough to enable the sensors permission.

2. If your app still uses **CocoaPods**, also add this in `ios/Podfile`:

   ```rb
    post_install do |installer|
        installer.pods_project.targets.each do |target|
            flutter_additional_ios_build_settings(target)

            ## ADD THIS SECTION
            target.build_configurations.each do |config|
                config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
                '$(inherited)',
                ## dart: PermissionGroup.sensors
                'PERMISSION_SENSORS=1',
                ]
            end
            ## END OF WHAT YOU NEED TO ADD
        end
    end
   ```

   If you already have a `target.build_configurations.each do |config|` loop in your `Podfile`, only include the `config.build_settings` section inside that loop.
   If you already use *permission_handler*, ensure `PERMISSION_SENSORS` is set to `1` instead of `0`.

</details>

<details open>
  <summary><b>Android</b></summary>

- You do not need to add `ACTIVITY_RECOGNITION` to your Android manifest. It is merged from the plugin.
- Requires **Android 10 (minSdk 29)**. Set this in `android/app/build.gradle` (or `.kts`).
- Make sure AndroidX is enabled (already true on recent Flutter projects).

</details>

## How to use it

1. <details open>
   <summary><b>Request permissions</b></summary>

    - Using *permission_handler*, you can request permission on both platforms.
    - The plugin includes `openAppSettings()` so the user can enable permissions if the system prompt is not shown again.
        #### Behavior
    - The first request shows a system dialog. Later calls return the stored answer or the current OS setting.
    - If permission is denied, show a snackbar or dialog and ask the user to update settings.
        #### Example:
        ```dart
        import 'package:permission_handler/permission_handler.dart';

        PermissionStatus perm =
        Platform.isAndroid ? await Permission.activityRecognition.request() : await Permission.sensors.request();
        print('perm: $perm');

        if (perm.isDenied || perm.isPermanentlyDenied || perm.isRestricted) {
            ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                    content: Text(
                        'You need to approve the permissions to use the pedometer',
                        style: TextStyle(
                        color: Theme.of(context).colorScheme.onError,
                        fontWeight: FontWeight.bold,
                        ),
                    ),
                    backgroundColor: Theme.of(context).colorScheme.errorContainer,
                    action: SnackBarAction(
                        label: 'Settings',
                        textColor: Theme.of(context).colorScheme.onError,
                        onPressed: () => openAppSettings(),
                    ),
                ),
            );
        } else {
            // Call the functions you need to read stepCount
        }
        ```
    </details>

2. <details open>
   <summary><b>Get step count</b></summary>

    - Request the total steps in a time range (`from` / `to`).
    - If `from` or `to` is omitted, it defaults to the maximum recorded window: 7 days on iOS, 10 days on Android with GMS.
    - If the range is longer than the platform window, the result only covers that window.
        #### Behavior
    - The first request after install may return `0`, because recording starts after permission is granted.
    - Reinstalling the app also resets this to `0` the first time.
    - With GMS, the number is close to Google Fit. Without GMS, Android returns steps **since last boot** only.
        #### Example:
        ```dart
        import 'package:pedometer_pro/pedometer_pro.dart';

        DateTime now = DateTime.now();
        DateTime from = now.subtract(Duration(days: now.weekday - 1));
        DateTime to = now.add(Duration(days: DateTime.daysPerWeek - now.weekday));

        int steps = await Pedometer().getStepCount(from: from, to: to);
        print('steps: $steps');
        ```
    </details>

3. <details open>
    <summary><b>Real-time step count (Stream) and steps since last boot</b></summary>

    - The first event is steps since last system boot. Further events stream as the user walks.
    - If the value is `0`, the stream may not fire until the user takes a step.
        #### Behavior
    - After a reboot (or a manual date change), the value resets to `0`.
        #### Example:
        ```dart
        StreamSubscription? _subStepCount;

        @override
        void initState() {
            super.initState();
            _listenToSteps();
        }

        @override
        void dispose() {
            _subStepCount?.cancel();
            super.dispose();
        }

        _listenToSteps() {
            _subStepCount = Pedometer().stepCountStream().listen((steps) => print('Steps: $steps'));
        }
        ```
    </details>

4. <details open>
    <summary><b>Real-time pedestrian status: Walking, Stopped (Stream)</b></summary>

    - Returns `stopped` or `walking`. On error it returns `unknown`.
        #### Behavior
    - The stream may not emit until the status changes. Assume `stopped` at start.
        #### Example:
        ```dart
        StreamSubscription? _subPedestrianStatus;

        @override
        void initState() {
            super.initState();
            _listenToStatus();
        }

        @override
        void dispose() {
            _subPedestrianStatus?.cancel();
            super.dispose();
        }

        _listenToStatus() {
            _subPedestrianStatus = Pedometer().pedestrianStatusStream().listen((status) => print('Status: $status'));
        }
        ```
    </details>

5. <details open>
    <summary><b>[iOS] Real-time step count since a date (Stream)</b></summary>

    - **Android alternative:** combine `getStepCount` and `stepCountStream`. See the example app.
    - Returns steps from `from` to `now()`, then keeps streaming.
        #### Behavior
    - Same as `stepCountStream`: first call may be `0` until the user walks.
    - iOS stores at most 7 days.
        #### Example:
        ```dart
        StreamSubscription? _subStepFrom;

        @override
        void initState() {
            super.initState();
            _listenToSteps();
        }

        @override
        void dispose() {
            _subStepFrom?.cancel();
            super.dispose();
        }

        _listenToSteps() {
            DateTime now = DateTime.now();
            DateTime from = now.subtract(Duration(days: now.weekday - 1));
            _subStepFrom = Pedometer().stepCountStreamFrom(from: from).listen((steps) => print('Steps: $steps'));
        }
        ```
    </details>

## Things to consider

The APIs may be missing or behave differently on some devices:

- Some Samsung phones do not support the Sensors API.
- Older iPhones do not support Pedestrian Status.
- OEMs use different sensors and OS policies, so step totals can differ (mainly Android).
- Without Google Play Services, Android `getStepCount` cannot reconstruct history from before the last reboot.

If the step sensor is not available, an error is thrown. The app must handle it.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) / [CHANGELOG_zh.md](CHANGELOG_zh.md).

## Thanks and credits

This package was originally forked from:

- [Pedometer_plus](https://pub.dev/packages/pedometer_plus) by [akaboshinit.dev](https://pub.dev/publishers/akaboshinit.dev/packages)

Which was originally forked and inspired by:

- [Pedometer](https://pub.dev/packages/pedometer) by [cachet.dk](https://pub.dev/publishers/cachet.dk/packages)
- [Simple_pedometer](https://pub.dev/packages/simple_pedometer) by [bookm.me](https://pub.dev/publishers/bookm.me/packages)

The example app was inspired by [Purrweb Agency - Dribbble](https://dribbble.com/shots/22762014-Step-Counter-Mobile-iOS-App).

## Package status

| Pub v. | Points | Popularity |
| --- | --- | --- |
| [![pub package](https://img.shields.io/pub/v/pedometer_pro.svg)](https://pub.dev/packages/pedometer_pro) | [![pub points](https://img.shields.io/pub/points/pedometer_pro)](https://pub.dev/packages/pedometer_pro/score) | [![popularity](https://img.shields.io/pub/popularity/pedometer_pro)](https://pub.dev/packages/pedometer_pro/score) |

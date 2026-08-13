import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:pedometer_pro/pedometer_pro.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const PedometerProExampleApp());
}

class PedometerProExampleApp extends StatelessWidget {
  const PedometerProExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Pedometer Pro',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: const PedometerHomePage(),
    );
  }
}

class _DaySteps {
  const _DaySteps(this.day, this.steps);
  final DateTime day;
  final int steps;
}

class PedometerHomePage extends StatefulWidget {
  const PedometerHomePage({super.key});

  @override
  State<PedometerHomePage> createState() => _PedometerHomePageState();
}

class _PedometerHomePageState extends State<PedometerHomePage> {
  final Pedometer _pedometer = Pedometer();

  /// iOS stores about 7 days; Android Recording API stores about 10.
  int get _historyDays => Platform.isIOS ? 7 : 10;

  PermissionStatus? _permission;
  int? _todaySteps;
  int? _historyTotal;
  List<_DaySteps> _daily = const [];
  bool _sinceBootFallback = false;
  String? _rangeError;
  bool _loadingRange = false;

  int? _streamSteps;
  int? _streamFromToday;
  PedestrianStatus? _status;
  String? _streamError;
  String? _statusError;

  StreamSubscription<int>? _stepSub;
  StreamSubscription<int>? _stepFromSub;
  StreamSubscription<PedestrianStatus>? _statusSub;

  @override
  void dispose() {
    _stepSub?.cancel();
    _stepFromSub?.cancel();
    _statusSub?.cancel();
    super.dispose();
  }

  Future<void> _requestPermission() async {
    final status = Platform.isAndroid
        ? await Permission.activityRecognition.request()
        : await Permission.sensors.request();
    setState(() => _permission = status);
  }

  bool get _granted {
    final status = _permission;
    return status == PermissionStatus.granted ||
        status == PermissionStatus.limited;
  }

  Future<void> _loadHistory() async {
    setState(() {
      _loadingRange = true;
      _rangeError = null;
      _sinceBootFallback = false;
    });
    try {
      final now = DateTime.now();
      final todayStart = DateTime(now.year, now.month, now.day);
      final historyStart =
          todayStart.subtract(Duration(days: _historyDays - 1));

      final today = await _pedometer.getStepCount(from: todayStart, to: now);
      final historyTotal =
          await _pedometer.getStepCount(from: historyStart, to: now);

      // Sequential: Android RecordingClient is not safe to hit in parallel.
      final daily = <_DaySteps>[];
      for (var i = 0; i < _historyDays; i++) {
        final day = todayStart.subtract(Duration(days: _historyDays - 1 - i));
        final dayEnd = DateTime(day.year, day.month, day.day, 23, 59, 59);
        final end = dayEnd.isAfter(now) ? now : dayEnd;
        final steps = await _pedometer.getStepCount(from: day, to: end);
        daily.add(_DaySteps(day, steps));
      }

      // Without GMS, Android TYPE_STEP_COUNTER returns the same since-boot
      // total for every day that overlaps the current boot window.
      final nonzero = daily.where((d) => d.steps > 0).toList();
      final sinceBootFallback = Platform.isAndroid &&
          nonzero.length >= 2 &&
          nonzero.every((d) => d.steps == historyTotal);

      setState(() {
        _todaySteps = today;
        _historyTotal = historyTotal;
        _daily = daily;
        _sinceBootFallback = sinceBootFallback;
      });
    } catch (e) {
      setState(() => _rangeError = e.toString());
    } finally {
      setState(() => _loadingRange = false);
    }
  }

  void _listenStreams() {
    _stepSub?.cancel();
    _stepFromSub?.cancel();
    _statusSub?.cancel();
    setState(() {
      _streamError = null;
      _statusError = null;
      _streamFromToday = null;
    });

    _stepSub = _pedometer.stepCountStream().listen(
      (steps) => setState(() => _streamSteps = steps),
      onError: (Object e) => setState(() => _streamError = e.toString()),
    );
    _statusSub = _pedometer.pedestrianStatusStream().listen(
      (status) => setState(() => _status = status),
      onError: (Object e) => setState(() => _statusError = e.toString()),
    );

    if (Platform.isIOS) {
      final todayStart = DateTime(
        DateTime.now().year,
        DateTime.now().month,
        DateTime.now().day,
      );
      _stepFromSub = _pedometer.stepCountStreamFrom(from: todayStart).listen(
        (steps) => setState(() => _streamFromToday = steps),
        onError: (Object e) => setState(() => _streamError = e.toString()),
      );
    }
  }

  String _formatDay(DateTime day) {
    final m = day.month.toString().padLeft(2, '0');
    final d = day.day.toString().padLeft(2, '0');
    return '${day.year}-$m-$d';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pedometer Pro')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            Platform.isAndroid
                ? 'Android with Google Play: getStepCount uses the Recording API '
                    '(about $_historyDays days of history, including per-day totals). '
                    'Without GMS (Xiaomi / OPPO / vivo): live streams still work; '
                    'getStepCount falls back to TYPE_STEP_COUNTER (steps since last boot, no per-day split).'
                : 'iOS: getStepCount reads CoreMotion history (about 7 days).',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _requestPermission,
            child: const Text('Request permission'),
          ),
          const SizedBox(height: 8),
          Text('Permission: ${_permission?.name ?? 'not requested'}'),
          const Divider(height: 32),
          Text('History (getStepCount)',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          FilledButton.tonal(
            onPressed: _granted ? _loadHistory : null,
            child: Text(
              'Load history (today + last $_historyDays days)',
            ),
          ),
          const SizedBox(height: 8),
          if (_loadingRange) const LinearProgressIndicator(),
          if (_rangeError != null)
            Text(_rangeError!, style: const TextStyle(color: Colors.red))
          else ...[
            Text('Today: ${_todaySteps ?? '-'}'),
            Text('Last $_historyDays days: ${_historyTotal ?? '-'}'),
            if (_sinceBootFallback) ...[
              const SizedBox(height: 8),
              Text(
                'This device has no Google Play Recording history. '
                'The number above is steps since last boot, not a per-day breakdown.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ] else if (_daily.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text('Per day', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 4),
              for (final entry in _daily)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    children: [
                      SizedBox(width: 120, child: Text(_formatDay(entry.day))),
                      Text('${entry.steps}'),
                    ],
                  ),
                ),
            ],
          ],
          const Divider(height: 32),
          Text('Live streams', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          FilledButton.tonal(
            onPressed: _granted ? _listenStreams : null,
            child: const Text('Start live streams'),
          ),
          const SizedBox(height: 8),
          Text(_streamError ?? 'Steps since boot: ${_streamSteps ?? '-'}'),
          if (Platform.isIOS)
            Text('Steps from today (stream): ${_streamFromToday ?? '-'}'),
          Text(_statusError ?? 'Pedestrian status: ${_status?.name ?? '-'}'),
          if (_permission == PermissionStatus.denied ||
              _permission == PermissionStatus.permanentlyDenied)
            TextButton(
              onPressed: openAppSettings,
              child: const Text('Open app settings'),
            ),
        ],
      ),
    );
  }
}

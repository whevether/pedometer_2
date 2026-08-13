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

class PedometerHomePage extends StatefulWidget {
  const PedometerHomePage({super.key});

  @override
  State<PedometerHomePage> createState() => _PedometerHomePageState();
}

class _PedometerHomePageState extends State<PedometerHomePage> {
  final Pedometer _pedometer = Pedometer();

  PermissionStatus? _permission;
  int? _rangeSteps;
  int? _streamSteps;
  PedestrianStatus? _status;
  String? _rangeError;
  String? _streamError;
  String? _statusError;
  bool _loadingRange = false;

  StreamSubscription<int>? _stepSub;
  StreamSubscription<PedestrianStatus>? _statusSub;

  @override
  void dispose() {
    _stepSub?.cancel();
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

  Future<void> _loadRange() async {
    setState(() {
      _loadingRange = true;
      _rangeError = null;
    });
    try {
      final now = DateTime.now();
      final from = DateTime(now.year, now.month, now.day);
      final steps = await _pedometer.getStepCount(from: from, to: now);
      setState(() => _rangeSteps = steps);
    } catch (e) {
      setState(() => _rangeError = e.toString());
    } finally {
      setState(() => _loadingRange = false);
    }
  }

  void _listenStreams() {
    _stepSub?.cancel();
    _statusSub?.cancel();
    setState(() {
      _streamError = null;
      _statusError = null;
    });

    _stepSub = _pedometer.stepCountStream().listen(
      (steps) => setState(() => _streamSteps = steps),
      onError: (Object e) => setState(() => _streamError = e.toString()),
    );
    _statusSub = _pedometer.pedestrianStatusStream().listen(
      (status) => setState(() => _status = status),
      onError: (Object e) => setState(() => _statusError = e.toString()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pedometer Pro')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'On Android, getStepCount uses Google Play Recording API when GMS is present, '
            'and TYPE_STEP_COUNTER (steps since last boot) on Xiaomi / OPPO / vivo without GMS.',
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
          FilledButton.tonal(
            onPressed: _granted ? _loadRange : null,
            child: const Text('getStepCount (today → now)'),
          ),
          const SizedBox(height: 8),
          if (_loadingRange) const LinearProgressIndicator(),
          Text(_rangeError ?? 'Range steps: ${_rangeSteps ?? '-'}'),
          const SizedBox(height: 16),
          FilledButton.tonal(
            onPressed: _granted ? _listenStreams : null,
            child: const Text('Start live streams'),
          ),
          const SizedBox(height: 8),
          Text(_streamError ?? 'Steps since boot: ${_streamSteps ?? '-'}'),
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

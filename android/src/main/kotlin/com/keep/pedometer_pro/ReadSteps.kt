package com.keep.pedometer_pro

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataSet
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.request.LocalDataReadRequest
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun hasLocalRecording(context: Context): Boolean {
    val status = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
            context,
            LocalRecordingClient.LOCAL_RECORDING_CLIENT_STEPS_MIN_VERSION_CODE
        )
    } catch (e: Exception) {
        Log.d(TAG, "Play services check failed: $e")
        ConnectionResult.SERVICE_MISSING
    }
    return status == ConnectionResult.SUCCESS
}

fun ensureRecordingSubscription(context: Context) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    if (!hasLocalRecording(context)) {
        return
    }
    try {
        FitnessLocal.getLocalRecordingClient(context)
            .subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
            .addOnFailureListener { e -> Log.d(TAG, "subscribe failed: $e") }
    } catch (e: Exception) {
        Log.d(TAG, "subscribe unavailable: $e")
    }
}

fun getSteps(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding, startTime: ZonedDateTime, endTime: ZonedDateTime, result: MethodChannel.Result) {

    val context = flutterPluginBinding.applicationContext

    Log.d(TAG, "ActivityCompat: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)}")
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
        result.error("2", "Permissions have not been requested", Exception())
        return
    }

    HealthConnectSteps.read(context, startTime, endTime, result) {
        getStepsFromRecordingOrSensor(context, startTime, endTime, result)
    }
}

private fun getStepsFromRecordingOrSensor(
    context: Context,
    startTime: ZonedDateTime,
    endTime: ZonedDateTime,
    result: MethodChannel.Result
) {
    if (!hasLocalRecording(context)) {
        Log.d(TAG, "GMS unavailable, falling back to TYPE_STEP_COUNTER")
        getStepsFromSensor(context, startTime, endTime, result)
        return
    }

    try {
        val localRecordingClient: LocalRecordingClient = FitnessLocal.getLocalRecordingClient(context)
        Log.d(TAG, "localRecordingClient:")
        localRecordingClient.subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener {
                    try {
                        Log.d(TAG, "addOnSuccessListener:")
                        val deferred = GlobalScope.async { readLocalSteps(startTime, endTime, localRecordingClient) }
                        val steps = runBlocking { deferred.await() }
                        Log.d(TAG, "result: $steps")
                        // Local Recording only stores steps after this app subscribes.
                        // A successful empty read is not Google Fit / OEM health history.
                        if (steps == 0 && rangeIncludesNow(startTime, endTime)) {
                            Log.d(TAG, "Recording empty for current window, falling back to TYPE_STEP_COUNTER")
                            getStepsFromSensor(context, startTime, endTime, result)
                        } else {
                            result.success(steps)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Recording read failed, falling back to sensor: $e")
                        getStepsFromSensor(context, startTime, endTime, result)
                    }
                }.addOnFailureListener { e ->
                    Log.d(TAG, "subscribe failed, falling back to sensor: $e")
                    getStepsFromSensor(context, startTime, endTime, result)
                }
    } catch (e: Exception) {
        Log.d(TAG, "Recording API unavailable, falling back to sensor: $e")
        getStepsFromSensor(context, startTime, endTime, result)
    }
}

internal fun rangeIncludesNow(startTime: ZonedDateTime, endTime: ZonedDateTime): Boolean {
    val now = System.currentTimeMillis()
    val start = startTime.toInstant().toEpochMilli()
    val end = endTime.toInstant().toEpochMilli()
    return start < now && end >= now - 2000
}

fun getStepsFromSensor(
    context: Context,
    startTime: ZonedDateTime,
    endTime: ZonedDateTime,
    result: MethodChannel.Result
) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    if (sensor == null) {
        result.error(
            "1",
            "StepCount not available",
            "TYPE_STEP_COUNTER is not available on this device"
        )
        return
    }

    val delivered = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    var best = 0
    var settlePosted = false

    fun finish(sinceBoot: Int) {
        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val rangeStart = startTime.toInstant().toEpochMilli()
        val rangeEnd = endTime.toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        if (rangeEnd <= bootTimeMillis || rangeStart >= now) {
            Log.d(TAG, "sensor fallback: no overlap with since-boot window, returning 0")
            result.success(0)
            return
        }

        Log.d(TAG, "sensor fallback sinceBoot=$sinceBoot bootTime=$bootTimeMillis")
        result.success(sinceBoot)
    }

    lateinit var listener: SensorEventListener
    listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values[0].toInt()
            if (value > best) {
                best = value
            }
            // Samsung often emits 0 first, then the real since-boot total.
            if (value == 0 && !settlePosted) {
                settlePosted = true
                handler.postDelayed({
                    if (!delivered.compareAndSet(false, true)) {
                        return@postDelayed
                    }
                    handler.removeCallbacksAndMessages(null)
                    sensorManager.unregisterListener(listener)
                    finish(best)
                }, 500)
                return
            }
            if (!delivered.compareAndSet(false, true)) {
                return
            }
            handler.removeCallbacksAndMessages(null)
            sensorManager.unregisterListener(this)
            finish(best)
        }
    }

    val registered = sensorManager.registerListener(
        listener,
        sensor,
        SensorManager.SENSOR_DELAY_NORMAL
    )
    if (!registered) {
        result.error(
            "1",
            "StepCount not available",
            "Unable to register TYPE_STEP_COUNTER listener"
        )
        return
    }

    handler.postDelayed({
        if (delivered.compareAndSet(false, true)) {
            sensorManager.unregisterListener(listener)
            if (best > 0) {
                finish(best)
            } else {
                result.error(
                    "1",
                    "StepCount sensor timeout",
                    "No reading from TYPE_STEP_COUNTER within 5 seconds"
                )
            }
        }
    }, 5000)
}

suspend fun readLocalSteps(startTime: ZonedDateTime, endTime: ZonedDateTime, localRecordingClient: LocalRecordingClient): Int {
    Log.d(TAG, "readRequest:")
    // Read raw deltas instead of bucketByTime(1, DAYS). A "today" range is
    // shorter than one day, so day buckets often come back empty even when
    // the subscription has already recorded steps.
    val readRequest = LocalDataReadRequest.Builder()
            .read(LocalDataType.TYPE_STEP_COUNT_DELTA)
            .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
            .build()
    Log.d(TAG, "readRequest: $readRequest")

    val response = localRecordingClient.readData(readRequest).await()
    Log.d(TAG, "readLocalSteps: $response")
    val dataSets = if (response.buckets.isNotEmpty()) {
        response.buckets.flatMap { it.dataSets }
    } else {
        response.dataSets
    }
    Log.i(TAG, "dataSets-Size: ${dataSets.size} buckets-Size: ${response.buckets.size}")

    var steps = 0
    for (dataSet in dataSets) {
        steps += aggregatedSteps(dataSet)
    }
    Log.d(TAG, "readLocalSteps: $steps")
    return steps
}

private fun aggregatedSteps(dataSet: LocalDataSet): Int {
    Log.d(TAG, "aggregatedSteps:")
    var steps = 0
    Log.d(TAG, "aggregatedSteps-steps/0: $steps")
    for (dp in dataSet.dataPoints) {
        for (field in dp.dataType.fields) {
            steps += dp.getValue(field).asInt()
        }
    }
    Log.d(TAG, "aggregatedSteps-res: $steps")
    return steps
}

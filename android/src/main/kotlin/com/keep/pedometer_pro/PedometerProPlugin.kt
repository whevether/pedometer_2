package com.keep.pedometer_pro
import android.content.ContentValues.TAG
import android.hardware.Sensor
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime


class PedometerProPlugin: FlutterPlugin, ActivityAware {
  private lateinit var stepDetectionChannel: EventChannel
  private lateinit var stepCountChannel: EventChannel
  private lateinit var methodChannel: MethodChannel
  private var activityBinding: ActivityPluginBinding? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
      /// Create channels
      stepDetectionChannel = EventChannel(flutterPluginBinding.binaryMessenger, "status_detection")
      stepCountChannel = EventChannel(flutterPluginBinding.binaryMessenger, "step_count")
      methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "method_channel")

      /// Create handlers
      val stepDetectionHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_DETECTOR)
      val stepCountHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_COUNTER)
      val handler = StepsMethodHandler(flutterPluginBinding)

      /// Set handlers
      stepDetectionChannel.setStreamHandler(stepDetectionHandler)
      stepCountChannel.setStreamHandler(stepCountHandler)
      methodChannel.setMethodCallHandler(handler)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      stepDetectionChannel.setStreamHandler(null)
      stepCountChannel.setStreamHandler(null)
      methodChannel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activityBinding = binding
      HealthConnectSteps.setActivity(binding.activity)
      binding.addActivityResultListener(activityResultListener)
  }

  override fun onDetachedFromActivityForConfigChanges() {
      detachActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
      detachActivity()
  }

  private fun detachActivity() {
      activityBinding?.removeActivityResultListener(activityResultListener)
      activityBinding = null
      HealthConnectSteps.setActivity(null)
  }

  private val activityResultListener =
      PluginRegistry.ActivityResultListener { requestCode, resultCode, data ->
          HealthConnectSteps.onActivityResult(requestCode, resultCode, data)
      }
}

class StepsMethodHandler() : MethodChannel.MethodCallHandler {

  private lateinit var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding

  constructor(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) : this(){
      this.flutterPluginBinding = flutterPluginBinding
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "Method: ${call.method}")
      if(call.method == "getStepCount") {
          val arguments = (call.arguments as Map<String, Any>?) ?: mutableMapOf()
          val argEndTime = arguments["endTime"] as Long? ?: Instant.now().toEpochMilli()
          val argStartTime = arguments["startTime"] as Long?

          val endTime: ZonedDateTime = Instant.ofEpochMilli(argEndTime).atZone(ZoneId.systemDefault())
          val startTime: ZonedDateTime = if(argStartTime != null) Instant.ofEpochMilli(argStartTime).atZone(ZoneId.systemDefault()) else endTime.minusWeeks(1)
          Log.d(TAG, "endTime: $endTime")
          Log.d(TAG, "startTime: $startTime")
          return getSteps(this.flutterPluginBinding, startTime, endTime, result)
      } else {
          result.notImplemented()
      }
  }
}

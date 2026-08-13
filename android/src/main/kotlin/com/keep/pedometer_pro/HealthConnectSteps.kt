package com.keep.pedometer_pro

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal object HealthConnectSteps {
    const val REQUEST_CODE = 0x4843

    private val readStepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null
    private var pendingStart: ZonedDateTime? = null
    private var pendingEnd: ZonedDateTime? = null
    private var pendingFallback: (() -> Unit)? = null

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun read(
        context: android.content.Context,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        result: MethodChannel.Result,
        onUnavailable: () -> Unit
    ) {
        val status = try {
            HealthConnectClient.getSdkStatus(context)
        } catch (e: Exception) {
            Log.d(TAG, "Health Connect status check failed: $e")
            HealthConnectClient.SDK_UNAVAILABLE
        }

        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Log.d(TAG, "Health Connect not available (status=$status)")
            onUnavailable()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (readStepsPermission !in granted) {
                    val act = activity
                    if (act == null) {
                        Log.d(TAG, "Health Connect permission needed but no Activity")
                        mainHandler.post(onUnavailable)
                        return@launch
                    }
                    pendingResult = result
                    pendingStart = startTime
                    pendingEnd = endTime
                    pendingFallback = onUnavailable
                    val intent = PermissionController
                        .createRequestPermissionResultContract()
                        .createIntent(act, setOf(readStepsPermission))
                    mainHandler.post {
                        act.startActivityForResult(intent, REQUEST_CODE)
                    }
                    return@launch
                }

                val steps = aggregate(client, startTime, endTime)
                mainHandler.post {
                    deliver(steps, startTime, endTime, result, onUnavailable)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Health Connect read failed: $e")
                mainHandler.post(onUnavailable)
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) {
            return false
        }
        val result = pendingResult
        val startTime = pendingStart
        val endTime = pendingEnd
        val fallback = pendingFallback
        pendingResult = null
        pendingStart = null
        pendingEnd = null
        pendingFallback = null

        if (result == null || startTime == null || endTime == null || fallback == null) {
            return true
        }

        val context = activity?.applicationContext
        if (context == null) {
            fallback()
            return true
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (readStepsPermission !in granted) {
                    Log.d(TAG, "Health Connect READ_STEPS denied")
                    mainHandler.post(fallback)
                    return@launch
                }
                val steps = aggregate(client, startTime, endTime)
                mainHandler.post {
                    deliver(steps, startTime, endTime, result, fallback)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Health Connect read after permission failed: $e")
                mainHandler.post(fallback)
            }
        }
        return true
    }

    private fun deliver(
        steps: Long,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        result: MethodChannel.Result,
        onUnavailable: () -> Unit
    ) {
        Log.d(TAG, "Health Connect steps=$steps")
        if (steps > 0L) {
            result.success(steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        } else if (rangeIncludesNow(startTime, endTime)) {
            onUnavailable()
        } else {
            result.success(0)
        }
    }

    private suspend fun aggregate(
        client: HealthConnectClient,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime
    ): Long {
        val (start, end) = expandToFullDays(startTime.toInstant(), endTime.toInstant())
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    /// Samsung Health writes one 00:00–23:59 record per day. Health Connect
    /// time-prorates blobs that only partially overlap the query window.
    private fun expandToFullDays(start: Instant, end: Instant): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val startDay = start.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val endExclusive = end.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return startDay to endExclusive
    }
}

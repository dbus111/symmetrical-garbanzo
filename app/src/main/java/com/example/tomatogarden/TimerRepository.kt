package com.example.tomatogarden

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

data class AppSnapshot(
    val timer: TimerState,
    val remainingMs: Long,
    val records: List<FocusRecord>,
    val exactAlarmAvailable: Boolean,
    val notificationAvailable: Boolean,
)

class TimerRepository private constructor(private val context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
    private val database = FocusDatabase(context)
    private var timer = readState()

    @Synchronized fun snapshot(): AppSnapshot {
        reconcileLocked()
        return AppSnapshot(
            timer, TimerLogic.remaining(timer, now()), database.all(),
            canScheduleExactAlarms(), canPostNotifications()
        )
    }

    @Synchronized fun reconcile() {
        reconcileLocked()
    }

    @Synchronized fun start() {
        reconcileLocked()
        timer = TimerLogic.start(timer, now(), UUID.randomUUID().toString())
        persist()
        scheduleAlarm()
    }

    @Synchronized fun pause() {
        val point = now()
        reconcileLocked(point)
        timer = TimerLogic.pause(timer, point)
        persist()
        cancelAlarm()
    }

    @Synchronized fun reset() {
        reconcileLocked()
        timer = TimerLogic.reset(timer)
        persist()
        cancelAlarm()
    }

    @Synchronized fun skip() {
        reconcileLocked()
        timer = TimerLogic.skip(timer)
        persist()
        cancelAlarm()
    }

    @Synchronized fun changeDurations(durations: Durations) {
        reconcileLocked()
        timer = TimerLogic.changeDurations(timer, durations)
        persist()
    }

    @Synchronized fun restoreAlarm() {
        reconcileLocked()
        if (timer.status == TimerStatus.RUNNING) scheduleAlarm()
    }

    private fun reconcileLocked(point: TimePoint = now()) {
        val transition = TimerLogic.reconcile(timer, point)
        if (transition.completedPhase == null) return
        // The session ID is unique, so a retry cannot create a second tomato.
        transition.focusCompletion?.let(database::add)
        timer = transition.state
        persist()
        cancelAlarm()
        showPhaseNotification(transition.completedPhase)
    }

    private fun now(): TimePoint {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
        val bootId = if (bootCount >= 0) bootCount.toLong() else (wall - elapsed) / 120_000L
        return TimePoint(wall, elapsed, bootId)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarmManager(): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun scheduleAlarm() {
        if (timer.status != TimerStatus.RUNNING) return
        val manager = alarmManager()
        val trigger = SystemClock.elapsedRealtime() + TimerLogic.remaining(timer, now())
        val pendingIntent = alarmIntent()
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pendingIntent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pendingIntent)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pendingIntent)
        }
    }

    private fun cancelAlarm() = alarmManager().cancel(alarmIntent())

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager().canScheduleExactAlarms()

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun showPhaseNotification(phase: Phase) {
        if (!canPostNotifications()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("timer_finished", "番茄钟提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        val title = if (phase == Phase.FOCUS) "专注完成，收获一颗番茄" else "休息结束"
        val next = when (timer.phase) {
            Phase.FOCUS -> "准备开始下一次专注"
            Phase.SHORT_BREAK -> "可以开始短休息了"
            Phase.LONG_BREAK -> "可以开始长休息了"
        }
        val openIntent = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "timer_finished")
            .setSmallIcon(R.drawable.ic_tomato_notification)
            .setContentTitle(title)
            .setContentText(next)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(100, notification)
    }

    private fun persist() {
        val json = JSONObject().apply {
            put("phase", timer.phase.name)
            put("status", timer.status.name)
            put("focusMinutes", timer.durations.focusMinutes)
            put("shortBreakMinutes", timer.durations.shortBreakMinutes)
            put("longBreakMinutes", timer.durations.longBreakMinutes)
            put("remainingMs", timer.remainingMs)
            put("deadlineWallMs", timer.deadlineWallMs)
            put("deadlineElapsedMs", timer.deadlineElapsedMs)
            put("bootId", timer.bootId)
            put("completedFocuses", timer.completedFocuses)
            put("sessionId", timer.sessionId)
            put("focusStartedAtMs", timer.focusStartedAtMs)
            put("plannedFocusMinutes", timer.plannedFocusMinutes)
        }
        preferences.edit().putString("state", json.toString()).commit()
    }

    private fun readState(): TimerState {
        val raw = preferences.getString("state", null) ?: return TimerState()
        return try {
            val json = JSONObject(raw)
            val durations = Durations(
                json.getInt("focusMinutes"), json.getInt("shortBreakMinutes"),
                json.getInt("longBreakMinutes")
            )
            if (!durations.isValid()) return TimerState()
            TimerState(
                phase = Phase.valueOf(json.getString("phase")),
                status = TimerStatus.valueOf(json.getString("status")),
                durations = durations,
                remainingMs = json.getLong("remainingMs").coerceAtLeast(0L),
                deadlineWallMs = json.getLong("deadlineWallMs"),
                deadlineElapsedMs = json.getLong("deadlineElapsedMs"),
                bootId = json.getLong("bootId"),
                completedFocuses = json.getInt("completedFocuses").coerceAtLeast(0),
                sessionId = json.getString("sessionId"),
                focusStartedAtMs = json.getLong("focusStartedAtMs"),
                plannedFocusMinutes = json.getInt("plannedFocusMinutes"),
            )
        } catch (_: Exception) {
            TimerState()
        }
    }

    companion object {
        @Volatile private var instance: TimerRepository? = null
        fun get(context: Context): TimerRepository = instance ?: synchronized(this) {
            instance ?: TimerRepository(context.applicationContext).also { instance = it }
        }
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerRepository.get(context).reconcile()
    }
}

class TimerRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerRepository.get(context).restoreAlarm()
    }
}

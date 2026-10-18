package com.example.tomatogarden

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private val Tomato = Color(0xFFBE3B33)
private val Ink = Color(0xFF332B29)
private val Cream = Color(0xFFFFF9F5)
private val Soft = Color(0xFFF5EAE4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TomatoGardenApp() }
    }
}

@Composable
private fun TomatoGardenApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { TimerRepository.get(context) }
    var snapshot by remember { mutableStateOf(repository.snapshot()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { snapshot = repository.snapshot() }

    LaunchedEffect(repository) {
        while (true) {
            snapshot = repository.snapshot()
            delay(500L)
        }
    }

    fun update(action: () -> Unit) {
        action()
        snapshot = repository.snapshot()
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Tomato, onPrimary = Color.White, secondary = Color(0xFF687B53),
        background = Cream, surface = Color.White, onSurface = Ink
    )) {
        Scaffold(
            containerColor = Cream,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🍅", fontSize = 27.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("番茄园", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("计时", "统计", "番茄园", "设置").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(listOf("◷", "▥", "●", "⚙")[index], fontSize = 20.sp) },
                            label = { Text(title) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                when (selectedTab) {
                    0 -> TimerPage(
                        snapshot = snapshot,
                        onStartPause = {
                            update {
                                if (snapshot.timer.status == TimerStatus.RUNNING) repository.pause()
                                else repository.start()
                            }
                        },
                        onReset = { update { repository.reset() } },
                        onSkip = { update { repository.skip() } },
                        onNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onExactAlarm = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    })
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    })
                                }
                            }
                        }
                    )
                    1 -> StatisticsPage(snapshot.records)
                    2 -> GardenPage(snapshot.records)
                    else -> SettingsPage(snapshot.timer.durations) { durations ->
                        update { repository.changeDurations(durations) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerPage(
    snapshot: AppSnapshot,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarm: () -> Unit,
) {
    val state = snapshot.timer
    val phaseName = when (state.phase) {
        Phase.FOCUS -> "专注时间"
        Phase.SHORT_BREAK -> "短休息"
        Phase.LONG_BREAK -> "长休息"
    }
    Text(phaseName, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
    Text(
        when (state.status) {
            TimerStatus.READY -> "准备好了就开始吧"
            TimerStatus.RUNNING -> "正在计时，放心专注"
            TimerStatus.PAUSED -> "已暂停，可以继续"
        }, color = Color.DarkGray
    )
    Spacer(Modifier.height(26.dp))
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(260.dp).background(Soft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍅", fontSize = 41.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        formatTime(snapshot.remainingMs), fontSize = 58.sp,
                        fontWeight = FontWeight.Bold, color = Tomato
                    )
                    Text("剩余时间", color = Color.DarkGray)
                }
            }
            Spacer(Modifier.height(28.dp))
            val total = state.durations.minutesFor(state.phase) * 60_000L
            val progress = if (total == 0L) 0f else
                (1f - snapshot.remainingMs.toFloat() / total).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Tomato, trackColor = Soft
            )
            Spacer(Modifier.height(12.dp))
            Text("已完成 ${state.completedFocuses} 次专注 · 第 ${state.completedFocuses % 4 + 1} 轮", color = Ink)
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onStartPause, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(if (state.status == TimerStatus.RUNNING) "暂停" else if (state.status == TimerStatus.PAUSED) "继续" else "开始", fontSize = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("重置") }
        FilledTonalButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("跳过") }
    }
    Spacer(Modifier.height(16.dp))
    Text("专注完成后自动记一颗番茄，并准备下一段休息。", color = Color.DarkGray, fontSize = 13.sp)
    if (!snapshot.notificationAvailable) {
        Spacer(Modifier.height(18.dp))
        PermissionCard(
            "通知未开启",
            "请开启通知，阶段结束时才能收到提醒。",
            "开启通知", onNotifications
        )
    }
    if (!snapshot.exactAlarmAvailable) {
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            "精确提醒未开启",
            "计时仍可恢复，但后台提醒可能延迟。",
            "开启精确提醒", onExactAlarm
        )
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun PermissionCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Soft)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun StatisticsPage(records: List<FocusRecord>) {
    val today = startOfDay(System.currentTimeMillis())
    val week = startOfWeek(System.currentTimeMillis())
    val todayRecords = records.filter { it.completedAtMs >= today }
    val weekRecords = records.filter { it.completedAtMs >= week }
    Text("专注统计", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
    Text("每一次完成都会计入本地记录", color = Color.DarkGray)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("今天", todayRecords.size, todayRecords.sumOf { it.durationMinutes }, Modifier.weight(1f))
        MetricCard("本周", weekRecords.size, weekRecords.sumOf { it.durationMinutes }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(24.dp))
    Text("最近 7 天", fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    val daily = (6 downTo 0).map { offset ->
        val start = shiftDay(today, -offset)
        val end = shiftDay(start, 1)
        start to records.filter { it.completedAtMs in start until end }
    }
    val maxDaily = daily.maxOfOrNull { it.second.size }?.coerceAtLeast(1) ?: 1
    daily.forEach { (start, dayRecords) ->
        StatRow(
            SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date(start)),
            dayRecords.size,
            dayRecords.sumOf { it.durationMinutes },
            dayRecords.size.toFloat() / maxDaily
        )
    }
    Spacer(Modifier.height(22.dp))
    Text("最近 4 周", fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    val weekly = (3 downTo 0).map { offset ->
        val start = shiftWeek(week, -offset)
        val end = shiftWeek(start, 1)
        start to records.filter { it.completedAtMs in start until end }
    }
    val maxWeekly = weekly.maxOfOrNull { it.second.size }?.coerceAtLeast(1) ?: 1
    weekly.forEach { (start, weekRecords) ->
        StatRow(
            "${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(start))} 起",
            weekRecords.size,
            weekRecords.sumOf { it.durationMinutes },
            weekRecords.size.toFloat() / maxWeekly
        )
    }
    Spacer(Modifier.height(24.dp))
    Text("最近完成", fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (records.isEmpty()) Text("还没有专注记录，完成一次计时后会出现在这里。", color = Color.DarkGray)
    records.take(10).forEach { record ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(record.completedAtMs)))
            Text("${record.durationMinutes} 分钟 · 🍅", color = Tomato)
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun MetricCard(label: String, count: Int, minutes: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text(label, color = Color.DarkGray)
            Spacer(Modifier.height(8.dp))
            Text("$count 次", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Tomato)
            Text("$minutes 分钟", fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int, minutes: Int, progress: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("$count 次 · $minutes 分钟", color = Color.DarkGray)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = Tomato, trackColor = Soft
        )
    }
}

@Composable
private fun GardenPage(records: List<FocusRecord>) {
    Text("我的番茄园", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
    Text("完成一次专注，收获一颗番茄", color = Color.DarkGray)
    Spacer(Modifier.height(22.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🍅", fontSize = 64.sp)
            Spacer(Modifier.height(8.dp))
            Text("${records.size} 颗番茄", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tomato)
            Text("累计 ${records.sumOf { it.durationMinutes }} 分钟专注", color = Color.DarkGray)
        }
    }
    Spacer(Modifier.height(18.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Soft)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("收获墙", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (records.isEmpty()) {
                Text("第一颗番茄还在等你。", color = Color.DarkGray)
            } else {
                repeat(ceil(records.size.coerceAtMost(60) / 6.0).toInt()) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        repeat(6) { col ->
                            val visible = row * 6 + col < records.size.coerceAtMost(60)
                            Text(if (visible) "🍅" else " ", fontSize = 28.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (records.size > 60) Text("还有 ${records.size - 60} 颗番茄已收获", color = Ink)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SettingsPage(current: Durations, onSave: (Durations) -> Unit) {
    var focus by remember { mutableStateOf(current.focusMinutes.toString()) }
    var shortBreak by remember { mutableStateOf(current.shortBreakMinutes.toString()) }
    var longBreak by remember { mutableStateOf(current.longBreakMinutes.toString()) }
    var message by remember { mutableStateOf("") }

    Text("计时设置", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
    Text("修改分钟数，保存后从下一段完整计时开始生效", color = Color.DarkGray)
    Spacer(Modifier.height(20.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            DurationField("专注", focus, { focus = it }, "1–180 分钟")
            DurationField("短休息", shortBreak, { shortBreak = it }, "1–60 分钟")
            DurationField("长休息", longBreak, { longBreak = it }, "1–120 分钟")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val newDurations = Durations(
                    focus.toIntOrNull() ?: 0,
                    shortBreak.toIntOrNull() ?: 0,
                    longBreak.toIntOrNull() ?: 0
                )
                if (newDurations.isValid()) {
                    onSave(newDurations)
                    message = "已保存"
                } else {
                    message = "请输入有效时长：专注 1–180、短休 1–60、长休 1–120 分钟"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = if (message == "已保存") Color(0xFF4C6938) else Tomato)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("每完成 4 次专注，下一段休息为长休息。跳过专注不会获得番茄。", color = Color.DarkGray, fontSize = 13.sp)
}

@Composable
private fun DurationField(label: String, value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) onValueChange(it) },
        label = { Text(label) },
        supportingText = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatTime(millis: Long): String {
    val seconds = (millis + 999L) / 1000L
    return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfWeek(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(time)
    val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
    add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
}.timeInMillis

private fun shiftDay(time: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = time
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

private fun shiftWeek(time: Long, weeks: Int): Long = Calendar.getInstance().apply {
    timeInMillis = time
    add(Calendar.WEEK_OF_YEAR, weeks)
}.timeInMillis

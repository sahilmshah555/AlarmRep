package com.pixel.alarmpartitions

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class AlarmTime(val hour: Int, val minute: Int) {
    fun formatted(): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
    }
}

data class AlarmPartition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val times: List<AlarmTime>,
    val repeatDays: List<Int>,
    val isEnabled: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmPartitionScreen()
                }
            }
        }
    }
}

@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmPartitionScreen() {
    val context = LocalContext.current
    var partitions by remember { mutableStateOf(Storage.load(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isAccessibilityReady by remember {
        mutableStateOf(ClockAutomationService.isServiceEnabled(context))
    }

    // Keep UI synced with changes made from the Home Screen Widget
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                partitions = Storage.load(context)
                isAccessibilityReady = ClockAutomationService.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Alarm Partitions",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Preset")
                Spacer(Modifier.width(8.dp))
                Text("New Preset", fontWeight = FontWeight.Medium)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (!isAccessibilityReady) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Enable Stock Clock Automation",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Tap here to grant Accessibility permission so presets can automatically toggle OFF in Google Clock.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            items(partitions, key = { it.id }) { partition ->
                PixelPartitionCard(
                    partition = partition,
                    onToggle = { enabled ->
                        isAccessibilityReady = ClockAutomationService.isServiceEnabled(context)
                        if (enabled) {
                            ClockAutomationService.enablePartitionInStockClock(context, partition)
                        } else {
                            ClockAutomationService.disablePartitionInStockClock(context, partition)
                        }
                        val updated = partitions.map {
                            if (it.id == partition.id) it.copy(isEnabled = enabled) else it
                        }
                        partitions = updated
                        Storage.save(context, updated)
                    },
                    onDelete = {
                        val updated = partitions.filterNot { it.id == partition.id }
                        partitions = updated
                        Storage.save(context, updated)
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePresetDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { newPartition ->
                val updated = partitions + newPartition
                partitions = updated
                Storage.save(context, updated)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelPartitionCard(
    partition: AlarmPartition,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (partition.isEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partition.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRepeatDays(partition.repeatDays),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Preset",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = partition.isEnabled,
                        onCheckedChange = onToggle
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                partition.times.forEach { time ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (partition.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = time.formatted(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = if (partition.isEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePresetDialog(
    onDismiss: () -> Unit,
    onSave: (AlarmPartition) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var times by remember {
        mutableStateOf(
            listOf(
                AlarmTime(7, 0),
                AlarmTime(7, 15),
                AlarmTime(7, 30)
            )
        )
    }
    var selectedDays by remember {
        mutableStateOf(
            setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        )
    }

    val daysOrder = listOf(
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
        Calendar.SUNDAY to "S"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("New Alarm Partition", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name (e.g. Morning, Gym)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Text("Shared Repeat Days", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOrder.forEach { (calDay, label) ->
                        val isSelected = selectedDays.contains(calDay)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedDays = if (isSelected) selectedDays - calDay
                                    else selectedDays + calDay
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text("Alarms in this Partition", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    times.forEachIndexed { idx, time ->
                        AssistChip(
                            onClick = {
                                times = times.filterIndexed { i, _ -> i != idx }
                            },
                            label = { Text("${time.formatted()} ✕") }
                        )
                    }
                    AssistChip(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    times = (times + AlarmTime(h, m)).sortedWith(
                                        compareBy({ it.hour }, { it.minute })
                                    )
                                },
                                7,
                                0,
                                false
                            ).show()
                        },
                        label = { Text("+ Add Time") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && times.isNotEmpty(),
                onClick = {
                    onSave(
                        AlarmPartition(
                            name = name.trim(),
                            times = times,
                            repeatDays = selectedDays.toList()
                        )
                    )
                }
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatRepeatDays(days: List<Int>): String {
    if (days.isEmpty()) return "One-time alarms"
    if (days.size == 7) return "Every day"
    val map = mapOf(
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )
    return days.mapNotNull { map[it] }.joinToString(", ")
}

object Storage {
    private const val PREF_NAME = "pixel_alarm_partitions"
    private const val KEY_DATA = "partitions_json"

    fun load(context: Context): List<AlarmPartition> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null) ?: return defaultPresets()
        val list = mutableListOf<AlarmPartition>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val timesArr = obj.getJSONArray("times")
            val times = (0 until timesArr.length()).map {
                val t = timesArr.getJSONObject(it)
                AlarmTime(t.getInt("h"), t.getInt("m"))
            }
            val daysArr = obj.getJSONArray("days")
            val days = (0 until daysArr.length()).map { daysArr.getInt(it) }
            list.add(
                AlarmPartition(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    times = times,
                    repeatDays = days,
                    isEnabled = obj.getBoolean("enabled")
                )
            )
        }
        return list
    }

    fun save(context: Context, list: List<AlarmPartition>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("enabled", p.isEnabled)
                put("days", JSONArray(p.repeatDays))
                val tArr = JSONArray()
                p.times.forEach { t ->
                    tArr.put(JSONObject().apply {
                        put("h", t.hour)
                        put("m", t.minute)
                    })
                }
                put("times", tArr)
            }
            arr.put(obj)
        }
        // Use commit() so widget reads updated state synchronously
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, arr.toString())
            .commit()

        // Refresh Home Screen Widget immediately
        MainScope().launch {
            PartitionWidget().updateAll(context)
        }
    }

    private fun defaultPresets(): List<AlarmPartition> = listOf(
        AlarmPartition(
            name = "Morning",
            times = listOf(AlarmTime(6, 45), AlarmTime(7, 0), AlarmTime(7, 15)),
            repeatDays = listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            ),
            isEnabled = false
        ),
        AlarmPartition(
            name = "Power Nap",
            times = listOf(AlarmTime(14, 30), AlarmTime(14, 45)),
            repeatDays = emptyList(),
            isEnabled = false
        )
    )
}

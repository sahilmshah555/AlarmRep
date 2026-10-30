package com.pixel.alarmpartitions

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class PartitionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PartitionWidget()
}

class PartitionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val partitions = Storage.load(context)
            GlanceTheme {
                WidgetContent(partitions = partitions)
            }
        }
    }

    @Composable
    private fun WidgetContent(partitions: List<AlarmPartition>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(16.dp)
        ) {
            // Header Row (Tapping header opens the main app)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable(actionRunCallback<OpenAppAction>()),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Alarm Partitions",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (partitions.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No presets yet. Tap to add.",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(partitions, itemId = { it.id.hashCode().toLong() }) { partition ->
                        Box(modifier = GlanceModifier.padding(bottom = 8.dp)) {
                            PartitionWidgetRow(partition)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PartitionWidgetRow(partition: AlarmPartition) {
        val containerColor = if (partition.isEnabled) {
            GlanceTheme.colors.secondaryContainer
        } else {
            GlanceTheme.colors.surfaceVariant
        }
        val textColor = if (partition.isEnabled) {
            GlanceTheme.colors.onSecondaryContainer
        } else {
            GlanceTheme.colors.onSurfaceVariant
        }

        val toggleParams = actionParametersOf(
            TogglePresetAction.KEY_PARTITION_ID to partition.id
        )

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(containerColor)
                .cornerRadius(20.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionRunCallback<TogglePresetAction>(toggleParams)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = partition.name,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = partition.times.joinToString(" • ") { it.formatted() },
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = formatRepeatDays(partition.repeatDays),
                    style = TextStyle(
                        color = textColor,
                        fontSize = 11.sp
                    )
                )
            }

            Switch(
                checked = partition.isEnabled,
                onCheckedChange = actionRunCallback<TogglePresetAction>(toggleParams)
            )
        }
    }
}

class TogglePresetAction : ActionCallback {
    companion object {
        val KEY_PARTITION_ID = ActionParameters.Key<String>("partition_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val partitionId = parameters[KEY_PARTITION_ID] ?: return
        val partitions = Storage.load(context)
        val target = partitions.find { it.id == partitionId } ?: return
        val newEnabledState = !target.isEnabled

        // Trigger Stock Clock automation (Option C)
        if (newEnabledState) {
            ClockAutomationService.enablePartitionInStockClock(context, target)
        } else {
            ClockAutomationService.disablePartitionInStockClock(context, target)
        }

        // Save new state and re-render widget
        val updated = partitions.map {
            if (it.id == partitionId) it.copy(isEnabled = newEnabledState) else it
        }
        Storage.save(context, updated)
        PartitionWidget().update(context, glanceId)
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

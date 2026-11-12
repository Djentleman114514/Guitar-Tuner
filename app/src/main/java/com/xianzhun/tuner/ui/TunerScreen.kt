package com.xianzhun.tuner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xianzhun.tuner.TunerUiState
import com.xianzhun.tuner.core.GuitarString
import com.xianzhun.tuner.core.GuitarTuning
import com.xianzhun.tuner.core.SignalState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TunerScreen(
    state: TunerUiState,
    onAutoChanged: (Boolean) -> Unit,
    onStringSelected: (GuitarString) -> Unit,
    onTuningSelected: (GuitarTuning) -> Unit,
    onToggleListening: () -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val selected = state.reading?.target ?: state.manualString.takeUnless { state.isAuto }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ForkMark(Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("弦准", color = Cream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("GUITAR TUNER", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                    TextButton(onClick = { showHelp = true }, modifier = Modifier.testTag("help")) {
                        Text("使用帮助", color = Muted, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuitarTuning.entries.forEach { tuning ->
                        ModeChip(tuning.title, state.tuning == tuning,
                            Modifier.testTag("tuning_${tuning.name}")) { onTuningSelected(tuning) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.tuning.strings.joinToString(" ") { it.note }, color = Cream,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("A4 = 440 Hz", color = Muted, fontSize = 11.sp)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModeChip("自动", state.isAuto, Modifier.testTag("mode_auto")) { onAutoChanged(true) }
                        ModeChip("手动", !state.isAuto, Modifier.testTag("mode_manual")) { onAutoChanged(false) }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Panel).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(6.dp).background(if (state.isListening) Accent else Muted, CircleShape))
                        Text(
                            if (state.isListening) "正在聆听" else "准备好，就拨响一根弦",
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 6.dp).semantics {
                            contentDescription = selected?.let { "目标 ${it.number} 弦 ${it.fullNote}" } ?: "等待识别目标弦"
                        },
                    ) {
                        Text(
                            selected?.note ?: "—", color = if (state.reading?.inTune == true) Accent else Cream,
                            fontSize = 72.sp, fontWeight = FontWeight.Light, lineHeight = 78.sp,
                        )
                        if (selected != null) Text(
                            selected.octave.toString(), color = Muted, fontSize = 24.sp,
                            modifier = Modifier.padding(start = 3.dp, bottom = 15.dp),
                        )
                    }
                    val frequencyText = state.reading?.let { "%.1f Hz".format(Locale.US, it.frequency) }
                        ?: selected?.let { "目标 %.2f Hz".format(Locale.US, it.frequency) } ?: "等待识别频率"
                    Text(frequencyText, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    TuningGauge(state.reading?.cents?.toFloat() ?: 0f, state.reading != null, state.reading?.inTune == true)
                    val status = when {
                        state.error != null -> "需要检查麦克风"
                        !state.isListening -> "点击下方，开始调音"
                        state.signal == SignalState.CLIPPED -> "声音过大，稍远一点"
                        state.signal == SignalState.UNSTABLE -> "尚未识别音高，请再拨弦"
                        state.signal == SignalState.TOO_QUIET -> "等待拨弦"
                        state.reading?.inTune == true -> "音准刚刚好"
                        state.reading == null -> "等待拨弦"
                        state.reading.cents < -3 -> "音偏低 · 升高音高"
                        state.reading.cents > 3 -> "音偏高 · 降低音高"
                        else -> "接近了 · 保持稳定"
                    }
                    Text(
                        status, color = if (state.reading?.inTune == true) Accent else Cream,
                        fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("tuning_status"),
                    )
                    Text(
                        state.reading?.takeIf { state.signal == SignalState.VALID }?.let {
                            "%+.1f 音分".format(Locale.US, it.cents)
                        } ?: "每次拨一根弦，让余音自然延续",
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.isAuto) "自动识别琴弦" else "选择要调的琴弦", color = Muted, fontSize = 12.sp)
                        Text("粗弦 → 细弦", color = Muted, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        state.tuning.strings.forEach { string ->
                            val active = selected == string
                            Column(
                                Modifier.weight(1f).testTag("string_${string.number}")
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (active) Accent else Panel)
                                    .clickable(role = Role.RadioButton) { onStringSelected(string) }
                                    .semantics {
                                        contentDescription = "${string.number} 弦 ${string.fullNote}"
                                        stateDescription = if (active) "已选择" else "未选择"
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(string.note, fontSize = 24.sp, color = if (active) Ink else Cream, fontWeight = FontWeight.Medium)
                                Text("${string.number} 弦", fontSize = 10.sp, color = if (active) Ink.copy(alpha = 0.7f) else Muted)
                            }
                        }
                    }
                    Text("点击琴弦可固定目标，适合音高偏离较多时使用", color = Muted, fontSize = 10.sp)
                }

            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error != null) {
                    Text(state.error, color = Amber, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("error"))
                }
                Button(
                    onClick = onToggleListening,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("listen_toggle"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isListening) Panel else Accent,
                        contentColor = if (state.isListening) Cream else Ink),
                ) {
                    Text(when {
                        state.isListening -> "暂停聆听"
                        state.permissionBlocked -> "前往设置，允许麦克风"
                        else -> "开始调音"
                    }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    if (state.isListening) InputMeter(state.level) else Text("→", fontSize = 21.sp)
                }
                Text("离线运行 · 声音不保存、不上传", color = Muted, fontSize = 10.sp)
            }
        }
    }

    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text("让每根弦，回到音准") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("1. 点击开始，允许麦克风。把手机放在吉他附近，一次拨响一根空弦。")
                Text("2. 先选择标准六弦或七弦 Drop A。自动模式会匹配所选定弦；如果识别不对，点击下方琴弦固定目标。一弦最细。")
                Text("3. 按提示升高或降低音高。指针居中并稳定后，会显示“音准刚刚好”。")
                Text("建议在安静环境使用。没有声音时，请检查系统麦克风开关。切到后台或锁屏会停止采音，返回时恢复。")
                Text("七弦 Drop A 的第七弦为 A1（55 Hz），其余六弦保持标准定弦。A4 = 440 Hz。100 音分等于一个半音。")
                Text("弦准 0.2.0 · 内测版\n声音只在本机内存处理，不保存、不上传。", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Ink else Muted, fontSize = 12.sp,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Accent else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "已选择" else "未选择" }
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}

@Composable
private fun TuningGauge(cents: Float, hasReading: Boolean, inTune: Boolean) {
    val animated by animateFloatAsState(cents.coerceIn(-50f, 50f), tween(90), label = "needle")
    Column(Modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(84.dp).clipToBounds().semantics {
            contentDescription = if (hasReading) "音高偏差 ${cents.toInt()} 音分" else "等待音高输入"
        }) {
            val radius = size.width * 0.62f
            val pivot = Offset(size.width / 2, radius + 6.dp.toPx())
            for (tick in -10..10) {
                val radians = (-90.0 + tick * 4.0) * PI / 180
                val outer = Offset(pivot.x + cos(radians).toFloat() * radius, pivot.y + sin(radians).toFloat() * radius)
                val innerRadius = radius - if (tick % 5 == 0) 16.dp.toPx() else 8.dp.toPx()
                val inner = Offset(pivot.x + cos(radians).toFloat() * innerRadius, pivot.y + sin(radians).toFloat() * innerRadius)
                drawLine(if (tick == 0) Accent else Muted.copy(alpha = 0.45f), inner, outer,
                    if (tick == 0) 3.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
            }
            val radians = (-90.0 + animated * 0.8) * PI / 180
            val end = Offset(pivot.x + cos(radians).toFloat() * (radius - 23.dp.toPx()),
                pivot.y + sin(radians).toFloat() * (radius - 23.dp.toPx()))
            val color = when { !hasReading -> Muted.copy(alpha = 0.35f); inTune -> Accent; else -> Amber }
            drawLine(color, pivot, end, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(color, 5.dp.toPx(), end)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−50", fontSize = 9.sp, color = Muted)
            Text("0", fontSize = 9.sp, color = Accent)
            Text("+50", fontSize = 9.sp, color = Muted)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun InputMeter(level: Double) {
    Canvas(Modifier.width(24.dp).height(19.dp).semantics { contentDescription = "麦克风输入强度" }) {
        for (bar in 0..4) {
            val fraction = (level * 12).coerceIn(0.12, 1.0).toFloat()
            val height = size.height * fraction * (if (bar % 2 == 0) 0.6f else 1f)
            val x = size.width * (bar + 0.5f) / 5
            drawLine(Accent, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2),
                2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ForkMark(modifier: Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.12f)
            lineTo(size.width * 0.28f, size.height * 0.49f)
            cubicTo(size.width * 0.28f, size.height * 0.8f, size.width * 0.72f, size.height * 0.8f,
                size.width * 0.72f, size.height * 0.49f)
            lineTo(size.width * 0.72f, size.height * 0.12f)
        }
        drawPath(path, Accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(Accent, Offset(size.width * 0.5f, size.height * 0.7f),
            Offset(size.width * 0.5f, size.height * 0.96f), 2.5.dp.toPx(), StrokeCap.Round)
        drawCircle(Accent, 1.6.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.12f))
    }
}

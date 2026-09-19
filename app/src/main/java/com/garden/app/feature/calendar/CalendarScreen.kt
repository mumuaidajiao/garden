package com.garden.app.feature.calendar

import com.garden.app.core.config.Personas
import com.garden.app.core.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garden.app.core.net.CalDay
import com.garden.app.core.net.CalendarData
import com.garden.app.core.net.CalItem
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历。
 *
 * 三块：
 *   ① 倒计时 —— 离她的生日、你们的纪念日还有多少天
 *   ② 月历 —— 重要日子高亮，她自己加的日程也标出来
 *   ③ 加一件 —— 她随手记的（"下周三去姥姥家"），能加能删
 */
@Composable
fun CalendarScreen(
    data: CalendarData?,
    loading: Boolean,
    onAdd: (String, String) -> Unit,   // (日期, 标题)
    onDelete: (Int) -> Unit,
    onBack: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .statusBarsPadding()
    ) {
        // ── 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = GardenText.glyph, color = TextDim)
            }
            Spacer(Modifier.width(6.dp))
            Text("日历", fontSize = GardenText.heading, color = TextMain, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PinkDeep)
                    .clickable { adding = true }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("加一件", fontSize = GardenText.body, color = Color.White)
            }
        }

        if (loading && data == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("正在翻日历……", fontSize = GardenText.bodyLg, color = TextDim)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── ① 倒计时
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .padding(16.dp)
                ) {
                    Text("还有多久", fontSize = GardenText.small, color = TextDim)
                    Spacer(Modifier.height(10.dp))
                    (data?.days ?: emptyList()).forEach { d -> CountdownRow(d) }
                }
            }

            // ── ② 月历
            item {
                MonthGrid(
                    month = month,
                    days = data?.days ?: emptyList(),
                    mine = data?.mine ?: emptyList(),
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) }
                )
            }

            // ── ③ 她自己加的
            val mine = (data?.mine ?: emptyList()).sortedBy { it.date }
            if (mine.isNotEmpty()) {
                item {
                    Text("自己记的", fontSize = GardenText.small, color = TextDim)
                }
                items(mine) { it -> MyDayRow(it, onDelete) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (adding) {
        AddDayDialog(
            onDismiss = { adding = false },
            onSave = { date, title ->
                onAdd(date, title)
                adding = false
            }
        )
    }
}

@Composable
private fun CountdownRow(d: CalDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = d.title,
            fontSize = GardenText.bodyLg,
            color = if (d.isToday) PinkLight else TextMain,
            fontWeight = if (d.isToday) FontWeight.Medium else FontWeight.Normal
        )
        if (d.lunar) {
            Spacer(Modifier.width(6.dp))
            Text("农历", fontSize = GardenText.tiny, color = TextDim)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (d.isToday) "就是今天" else "${d.daysLeft} 天",
            fontSize = GardenText.bodyLg,
            color = if (d.isToday) PinkLight else TextDim,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 月历。
 *
 * 重要日子和日程都标成小圆点 —— 不写字，因为格子太小，
 * 她想知道那天是什么，看下面的列表就行。
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    days: List<CalDay>,
    mine: List<CalItem>,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // 这个月里"有事情"的日子
    val marked = remember(month, days, mine) {
        val m = mutableSetOf<String>()
        days.forEach { d ->
            if (d.date.startsWith(month.toString())) m.add(d.date)
        }
        mine.forEach { i -> if (i.date.startsWith(month.toString())) m.add(i.date) }
        m
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clickable { onPrev() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = GardenText.button, color = TextDim) }

            Spacer(Modifier.weight(1f))
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                fontSize = GardenText.bodyLg,
                color = TextMain,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.size(34.dp).clickable { onNext() },
                contentAlignment = Alignment.Center
            ) { Text("›", fontSize = GardenText.button, color = TextDim) }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(
                    text = w,
                    fontSize = GardenText.label,
                    color = TextDim,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 从周一开头排，前面补空格
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - 1)      // 周一=0
        val total = month.lengthOfMonth()
        val cells = lead + total
        val rows = (cells + 6) / 7

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..total) {
                            val dateStr = "%04d-%02d-%02d".format(
                                month.year, month.monthValue, dayNum
                            )
                            val isToday = dateStr == LocalDate.now().toString()
                            val has = marked.contains(dateStr)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isToday) PinkDeep else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        fontSize = GardenText.small,
                                        color = if (isToday) Color.White else TextMain
                                    )
                                }
                                // 有事的那天点一个小点
                                Box(
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (has) HimGold else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyDayRow(item: CalItem, onDelete: (Int) -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable { confirm = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.date.substring(5), fontSize = GardenText.small, color = TextDim)
        Spacer(Modifier.width(12.dp))
        Text(item.title, fontSize = GardenText.bodyLg, color = TextMain)
        Spacer(Modifier.weight(1f))
        if (item.by == "him") {
            Text("${Personas.himCall}加的", fontSize = GardenText.label, color = HimGold)
        }
    }

    if (confirm) {
        Dialog(onDismissRequest = { confirm = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .padding(20.dp)
            ) {
                Text("把这条划掉？", fontSize = GardenText.title, color = TextMain)
                Spacer(Modifier.height(6.dp))
                Text("${item.date}  ${item.title}", fontSize = GardenText.small, color = TextDim)
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "算了",
                        fontSize = GardenText.body,
                        color = TextDim,
                        modifier = Modifier.clickable { confirm = false }.padding(10.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PinkDeep)
                            .clickable { onDelete(item.id); confirm = false }
                            .padding(horizontal = 20.dp, vertical = 9.dp)
                    ) {
                        Text("划掉", fontSize = GardenText.body, color = Color.White)
                    }
                }
            }
        }
    }
}

/** 加一件：日期 + 一句话 */
@Composable
private fun AddDayDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var title by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .padding(20.dp)
        ) {
            Text("记一件", fontSize = GardenText.title, color = PinkLight)
            Spacer(Modifier.height(14.dp))

            FieldBox(date) { date = it.take(10) }
            Spacer(Modifier.height(10.dp))
            FieldBox(title, hint = "什么事（比如：去姥姥家）") { title = it.take(40) }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "算了",
                    fontSize = GardenText.body,
                    color = TextDim,
                    modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (title.isBlank()) DisabledBg else PinkDeep)
                        .clickable(enabled = title.isNotBlank()) { onSave(date, title.trim()) }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text("记上", fontSize = GardenText.body, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FieldBox(
    value: String,
    hint: String = "",
    onChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgTop)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Text(hint, fontSize = GardenText.bodyLg, color = TextDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = TextMain, fontSize = GardenText.bodyLg),
            cursorBrush = SolidColor(PinkLight),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

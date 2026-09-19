package com.garden.app.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.CalendarData
import com.garden.app.core.net.RelayClient
import kotlinx.coroutines.launch

/**
 * 「日历」这一站的全部家当。
 *
 * 跟 ChatRoute 一个规矩：状态归自己管，退出即销毁。
 * 增删之后都重新拉一遍服务器那份，保证她看到的和别人看到的是同一份。
 */
@Composable
fun CalendarRoute(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf<CalendarData?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        when (val r = RelayClient.calendar()) {
            is ApiResult.Ok -> data = r.data
            is ApiResult.Err -> Unit
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        loading = false
    }

    CalendarScreen(
        data = data,
        loading = loading,
        onAdd = { date, title ->
            scope.launch {
                RelayClient.addCalItem(date, title)
                refresh()
            }
        },
        onDelete = { id ->
            scope.launch {
                RelayClient.delCalItem(id)
                refresh()
            }
        },
        onBack = onBack
    )
}

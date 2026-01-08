package com.example.studentmate.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Gson LocalDate 序列化器
class LocalDateSerializer : JsonSerializer<LocalDate> {
    override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

// Gson LocalDate 反序列化器
class LocalDateDeserializer : JsonDeserializer<LocalDate> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate {
        return LocalDate.parse(json.asString)
    }
}

// 创建带LocalDate支持的Gson实例
val gsonWithLocalDate: Gson = GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, LocalDateSerializer())
    .registerTypeAdapter(LocalDate::class.java, LocalDateDeserializer())
    .create()

data class CountdownItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val targetDate: LocalDate,
    val description: String = "",
    val colorInt: Int = 0xFFFF6B6B.toInt()
)

private val CountdownColors = listOf(
    0xFFFF6B6B.toInt(), 0xFF4ECDC4.toInt(), 0xFFFFE66D.toInt(),
    0xFF95E1D3.toInt(), 0xFFF38181.toInt(), 0xFFAA96DA.toInt(),
    0xFFFCBF49.toInt(), 0xFF2A9D8F.toInt()
)

@Composable
fun CountdownScreen() {
    val context = LocalContext.current
    val countdowns = remember { mutableStateListOf<CountdownItem>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<CountdownItem?>(null) }

    // 安全加载数据，避免崩溃
    LaunchedEffect(Unit) {
        try {
            val saved = loadCountdownsFromLocal(context)
            countdowns.clear()
            countdowns.addAll(saved)
        } catch (e: Exception) {
            e.printStackTrace()
            // 清空可能导致问题的数据
            val prefs = context.getSharedPreferences(PREFS_NAME_COUNTDOWN, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_COUNTDOWNS).apply()
        }
    }

    fun saveNow() {
        try {
            saveCountdownsToLocal(context, countdowns)
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    fun deleteItem(item: CountdownItem) {
        try {
            countdowns.remove(item)
            saveNow()
            Toast.makeText(context, "已删除: ${item.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏
            CountdownHeader()

            if (countdowns.isEmpty()) {
                // 空状态提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📅",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "还没有倒数日",
                            fontSize = 18.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "点击右下角 + 添加你的第一个倒计时",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // 倒数日列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(countdowns, key = { it.id }) { item ->
                        CountdownCard(
                            item = item,
                            onDelete = { deleteItem(item) },
                            onClick = { editingItem = item }
                        )
                    }
                }
            }
        }

        // 右下角添加按钮
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加", tint = Color.White)
        }

        // 添加弹窗
        if (showAddDialog) {
            CountdownDialog(
                title = "添加倒数日",
                initialItem = CountdownItem(
                    title = "",
                    targetDate = LocalDate.now().plusDays(1),
                    description = "",
                    colorInt = CountdownColors.random()
                ),
                onDismiss = { showAddDialog = false },
                onSave = { newItem ->
                    countdowns.add(newItem)
                    saveNow()
                    showAddDialog = false
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), newItem.targetDate)
                    Toast.makeText(context, "已添加: ${newItem.title}，还有 $days 天", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 编辑弹窗
        editingItem?.let { item ->
            CountdownDialog(
                title = "编辑倒数日",
                initialItem = item,
                onDismiss = { editingItem = null },
                onSave = { updatedItem ->
                    val index = countdowns.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        countdowns[index] = updatedItem
                        saveNow()
                    }
                    editingItem = null
                }
            )
        }
    }
}

@Composable
fun CountdownHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp)
    ) {
        Text(
            text = "倒数日",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "重要的日子，我来提醒你",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun CountdownCard(
    item: CountdownItem,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val daysRemaining = ChronoUnit.DAYS.between(today, item.targetDate)
    val isExpired = daysRemaining < 0
    val isToday = daysRemaining == 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧颜色条
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(60.dp)
                    .background(Color(item.colorInt), RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 中间内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                Text(
                    text = when {
                        isToday -> "就是今天！"
                        isExpired -> "已过去 ${-daysRemaining} 天"
                        else -> "还有 $daysRemaining 天"
                    },
                    fontSize = 14.sp,
                    color = when {
                        isToday -> Color.Red
                        isExpired -> Color.Gray
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = item.targetDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 右侧删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.Red.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// 自由日期选择对话框，使用Android原生DatePickerDialog保证稳定性
@Composable
fun CountdownDialog(
    title: String,
    initialItem: CountdownItem,
    onDismiss: () -> Unit,
    onSave: (CountdownItem) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialItem.title) }
    var description by remember { mutableStateOf(initialItem.description) }
    var selectedColor by remember { mutableStateOf(initialItem.colorInt) }
    var selectedDate by remember { mutableStateOf(initialItem.targetDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Android原生DatePickerDialog
    if (showDatePicker) {
        // 转换LocalDate到Calendar
        val calendar = java.util.Calendar.getInstance()
        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        
        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                showDatePicker = false
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        
        // 设置最小日期为今天
        datePickerDialog.datePicker.minDate = java.util.Calendar.getInstance().timeInMillis
        
        // 显示对话框
        DisposableEffect(Unit) {
            datePickerDialog.show()
            onDispose {
                datePickerDialog.dismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("事件名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 日期选择按钮
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "选择日期: ${selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 颜色选择
                Text("选择颜色", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CountdownColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(color), RoundedCornerShape(50))
                                .clickable { selectedColor = color }
                                .then(
                                    if (selectedColor == color) {
                                        Modifier.background(
                                            Color.Black.copy(alpha = 0.2f),
                                            RoundedCornerShape(50)
                                        )
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            initialItem.copy(
                                title = name.trim(),
                                description = description.trim(),
                                targetDate = selectedDate,
                                colorInt = selectedColor
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// --- 本地存储工具函数 ---
private const val PREFS_NAME_COUNTDOWN = "countdown_data"
private const val KEY_COUNTDOWNS = "countdown_list_json"

fun saveCountdownsToLocal(context: Context, list: List<CountdownItem>) {
    val prefs = context.getSharedPreferences(PREFS_NAME_COUNTDOWN, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    val jsonString = gsonWithLocalDate.toJson(list)
    editor.putString(KEY_COUNTDOWNS, jsonString)
    editor.apply()
}

fun loadCountdownsFromLocal(context: Context): List<CountdownItem> {
    val prefs = context.getSharedPreferences(PREFS_NAME_COUNTDOWN, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_COUNTDOWNS, null)
    return if (jsonString != null) {
        val type = object : TypeToken<List<CountdownItem>>() {}.type
        gsonWithLocalDate.fromJson(jsonString, type)
    } else {
        emptyList()
    }
}

package com.example.studentmate.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 定义本地数据模型
data class TodoItem(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    var isDone: Boolean = false
)

@Composable
fun TodoScreen() {
    val context = LocalContext.current

    // 1. 状态管理
    val todoList = remember { mutableStateListOf<TodoItem>() }
    var inputText by remember { mutableStateOf("") }

    // 2. 核心逻辑：进入页面时，读取本地保存的数据
    LaunchedEffect(Unit) {
        val savedList = loadTodosFromLocal(context)
        todoList.clear()
        todoList.addAll(savedList)
    }

    // 辅助函数：保存当前列表到手机
    fun saveNow() {
        saveTodosToLocal(context, todoList)
    }

    // 计算进度
    val totalCount = todoList.size
    val doneCount = todoList.count { it.isDone }
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "ProgressAnim")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6FC))
            .padding(16.dp)
    ) {
        // A. 顶部进度卡片
        Card(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("今日进度", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (totalCount == 0) "开始新一天!" else if (progress == 1f) "完美通关! 🎉" else "继续加油!",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    Text("已完成 $doneCount / $totalCount 项", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(80.dp), color = Color.White.copy(alpha = 0.3f), strokeWidth = 8.dp)
                    CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.size(80.dp), color = Color.White, strokeWidth = 8.dp, strokeCap = StrokeCap.Round)
                    Text("${(progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // B. 输入框
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("输入新任务...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        // 添加到列表头
                        todoList.add(0, TodoItem(content = inputText))
                        inputText = ""
                        // 🟢 立即保存到本地
                        saveNow()
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // C. 列表内容
        if (todoList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无任务，享受清闲时光~", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(todoList) { item ->
                    GamifiedTodoItem(
                        item = item,
                        onCheckedChange = { isChecked ->
                            val index = todoList.indexOf(item)
                            if (index != -1) {
                                // 更新状态
                                todoList[index] = item.copy(isDone = isChecked)
                                // 🟢 立即保存到本地
                                saveNow()
                            }
                        },
                        onDelete = {
                            todoList.remove(item)
                            // 🟢 立即保存到本地
                            saveNow()
                        }
                    )
                }
            }
        }
    }
}

// 底部单行组件
@Composable
fun GamifiedTodoItem(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isDone) 0.dp else 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onCheckedChange(!item.isDone) }) {
                if (item.isDone) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                } else {
                    Box(modifier = Modifier.size(24.dp).border(2.dp, Color.LightGray, CircleShape))
                }
            }
            Text(
                text = item.content,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = if (item.isDone) TextStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)
                else TextStyle(textDecoration = TextDecoration.None, color = Color(0xFF333333), fontWeight = FontWeight.Medium),
                fontSize = 16.sp
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFEEEEEE))
            }
        }
    }
}

// --- 🛠️ 本地储存工具函数 (使用 SharedPreferences + Gson) ---

private const val PREFS_NAME = "student_mate_local_data"
private const val KEY_TODOS = "todo_list_json"

// 保存数据
fun saveTodosToLocal(context: Context, list: List<TodoItem>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    // 使用 Gson 把列表变成字符串
    val jsonString = Gson().toJson(list)
    editor.putString(KEY_TODOS, jsonString)
    editor.apply()
}

// 读取数据
fun loadTodosFromLocal(context: Context): List<TodoItem> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_TODOS, null)

    return if (jsonString != null) {
        // 使用 Gson 把字符串变回列表
        val type = object : TypeToken<List<TodoItem>>() {}.type
        Gson().fromJson(jsonString, type)
    } else {
        emptyList()
    }
}
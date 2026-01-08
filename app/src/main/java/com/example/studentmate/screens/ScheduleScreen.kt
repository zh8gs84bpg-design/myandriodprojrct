package com.example.studentmate.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState // 🟢 修复：添加 ScrollState 导入
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search // 🟢 修复：改用 Search 图标，替换缺失的 Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jsoup.Jsoup // 核心：用于解析网页
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- 数据模型 ---
data class Course(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val room: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val step: Int,
    val colorInt: Int
)

// --- 界面常量 ---
val HeaderHeight = 50.dp
val SidebarWidth = 35.dp
val CourseHeight = 65.dp
const val MaxNodes = 12
val TotalScheduleHeight = CourseHeight * MaxNodes

// 预设高颜值颜色
val CourseColors = listOf(
    0xFF64B5F6.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(),
    0xFFE57373.toInt(), 0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt(), 0xFF90A4AE.toInt()
)

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current

    // 状态管理
    val courses = remember { mutableStateListOf<Course>() }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 新增：控制导入页面显示
    var showWebView by remember { mutableStateOf(false) }

    var currentWeekOffset by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val daysFromMonday = today.dayOfWeek.value - 1
    val mondayDate = today.minusDays(daysFromMonday.toLong()).plusWeeks(currentWeekOffset.toLong())
    val scrollState = rememberScrollState()

    // 1. 进入页面读取本地数据
    LaunchedEffect(Unit) {
        val savedCourses = loadCoursesFromLocal(context)
        courses.clear()
        courses.addAll(savedCourses)
    }

    // 辅助函数：保存到本地
    fun saveNow() {
        saveCoursesToLocal(context, courses)
    }

    // --- 🔥 核心：武汉理工大学专用课表解析逻辑 ---
    fun parseHtmlToCourses(html: String) {
        try {
            // 1. 严格内容校验：检查HTML是否包含课表相关内容
            val lowerHtml = html.toLowerCase()
            if (!(lowerHtml.contains("data-week") || 
                  lowerHtml.contains("wut_table") || 
                  lowerHtml.contains("学生课表") ||
                  lowerHtml.contains("课程表"))) {
                Toast.makeText(context, "未检测到课表数据，请先进入【学生课表】页面", Toast.LENGTH_LONG).show()
                return
            }

            // 2. 解析 HTML
            val doc = Jsoup.parse(html)

            // 3. 查找课表表格 - 武汉理工大学教务系统课表特征
            val tables = doc.select("table")
            val newCourses = mutableListOf<Course>()

            // 4. 遍历表格寻找课表
            for (table in tables) {
                val rows = table.select("tr")
                if (rows.size < 5) continue

                // 遍历每一行（从第二行开始，跳过表头）
                for (rowIndex in 1 until rows.size) {
                    val row = rows[rowIndex]
                    val cells = row.select("td")

                    // 遍历每一格（从第二列开始，跳过节次列）
                    for (colIndex in 1 until cells.size) {
                        val cell = cells[colIndex]
                        val text = cell.text().trim()

                        if (text.isNotEmpty() && text != " ") {
                            // 💡 武汉理工大学课表坐标计算
                            val day = colIndex  // 星期：1-7
                            val startNode = rowIndex  // 开始节次

                            if (day in 1..7 && startNode in 1..12) {
                                // 解析课程信息 - 武汉理工大学格式：课程名 教师名 教室名
                                val lines = text.split("\n").filter { it.isNotBlank() }
                                val courseName = lines.getOrNull(0) ?: "未知课程"

                                // 查找教室信息
                                var roomName = ""
                                for (line in lines) {
                                    if (line.contains("教") || line.contains("楼") || line.matches(Regex(".*\\d+.*"))) {
                                        roomName = line
                                        break
                                    }
                                }

                                // 计算课程节数（武汉理工大学课表中，跨节课程会合并单元格）
                                val rowspan = cell.attr("rowspan").toIntOrNull() ?: 1
                                val step = rowspan  // 节数

                                // 严格校验：课程名不能为空
                                if (courseName.isNotBlank() && courseName != "未知课程") {
                                    newCourses.add(
                                        Course(
                                            name = courseName,
                                            room = roomName,
                                            dayOfWeek = day,
                                            startNode = startNode,
                                            step = step,
                                            colorInt = CourseColors.random()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. 结果处理 - 只有解析到真正的课程才成功
            if (newCourses.isNotEmpty()) {
                // 严格校验：确保解析到的课程数量合理（1-40门课程）
                if (newCourses.size in 1..40) {
                    courses.clear()
                    courses.addAll(newCourses)
                    saveNow()
                    Toast.makeText(context, "成功导入 ${newCourses.size} 门课程！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "解析结果异常：检测到 ${newCourses.size} 门课程，请确认是否在正确的课表页面", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "未检测到课表数据，请先进入【学生课表】页面", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "解析出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            // 顶部栏
            ScheduleTopBar(
                currentDate = mondayDate,
                onPrevWeek = { currentWeekOffset-- },
                onNextWeek = { currentWeekOffset++ },
                onBackToToday = { currentWeekOffset = 0 }
            )
            // 星期头
            WeekHeader(mondayDate)

            // 课表主体
            Row(modifier = Modifier.weight(1f)) {
                TimeSidebar(scrollState)
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState)) {
                    val dayWidth = maxWidth / 7
                    Box(modifier = Modifier.height(TotalScheduleHeight).fillMaxWidth()) {
                        DrawGridLines(dayWidth)
                        courses.forEach { course ->
                            CourseCard(course, dayWidth) {
                                selectedCourse = course
                                showEditDialog = true
                            }
                        }
                    }
                }
            }
        }

        // 右下角悬浮按钮组
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 1. 导入按钮
            SmallFloatingActionButton(
                onClick = { showWebView = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // 🟢 修复：使用 Search 图标代替 Download
                Icon(Icons.Default.Search, contentDescription = "导入", tint = Color.White)
            }

            // 2. 添加按钮
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = Color.White)
            }
        }

        // 弹窗区域
        if (showEditDialog && selectedCourse != null) {
            CourseDialog(
                title = "编辑课程",
                initialCourse = selectedCourse!!,
                onDismiss = { showEditDialog = false },
                onDelete = {
                    courses.remove(selectedCourse)
                    saveNow()
                    showEditDialog = false
                },
                onSave = { updatedCourse ->
                    val index = courses.indexOf(selectedCourse)
                    if (index != -1) {
                        courses[index] = updatedCourse
                        saveNow()
                    }
                    showEditDialog = false
                }
            )
        }

        if (showAddDialog) {
            val newCourse = Course(name = "", room = "", dayOfWeek = 1, startNode = 1, step = 2, colorInt = CourseColors.random())
            CourseDialog("添加新课程", newCourse, true, { showAddDialog = false }, {}, { course ->
                courses.add(course)
                saveNow()
                showAddDialog = false
            })
        }

        // 3. 导入网页弹窗 (关键：这里调用另一个文件里的 WebViewScreen)
        if (showWebView) {
            WebViewScreen(
                onDismiss = { showWebView = false },
                onDataExtracted = { html ->
                    parseHtmlToCourses(html)
                    showWebView = false
                }
            )
        }
    }
}

// --- 辅助组件 ---

@Composable
fun CourseCard(course: Course, dayWidth: Dp, onClick: () -> Unit) {
    val offsetX = dayWidth * (course.dayOfWeek - 1)
    val offsetY = CourseHeight * (course.startNode - 1)
    val height = CourseHeight * course.step
    Card(
        modifier = Modifier.offset(x = offsetX, y = offsetY).width(dayWidth).height(height).padding(2.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(course.colorInt)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = course.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = "@" + course.room, fontSize = 8.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun CourseDialog(title: String, initialCourse: Course, isNewMode: Boolean = false, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember { mutableStateOf(initialCourse.name) }
    var room by remember { mutableStateOf(initialCourse.room) }
    var dayOfWeekStr by remember { mutableStateOf(initialCourse.dayOfWeek.toString()) }
    var startNodeStr by remember { mutableStateOf(initialCourse.startNode.toString()) }
    var stepStr by remember { mutableStateOf(initialCourse.step.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("教室") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = dayOfWeekStr, onValueChange = { dayOfWeekStr = it }, label = { Text("星期(1-7)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = startNodeStr, onValueChange = { startNodeStr = it }, label = { Text("开始节次") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val day = dayOfWeekStr.toIntOrNull()?.coerceIn(1, 7) ?: 1
                val start = startNodeStr.toIntOrNull()?.coerceIn(1, 12) ?: 1
                val step = stepStr.toIntOrNull()?.coerceIn(1, 4) ?: 2
                if (name.isNotEmpty()) onSave(initialCourse.copy(name = name, room = room, dayOfWeek = day, startNode = start, step = step))
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (!isNewMode) TextButton(onClick = onDelete) { Text("删除课程", color = Color.Red) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
fun ScheduleTopBar(currentDate: LocalDate, onPrevWeek: () -> Unit, onNextWeek: () -> Unit, onBackToToday: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevWeek) { Icon(Icons.Default.ArrowBack, contentDescription = "上一周") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("第 ${currentDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())} 周", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(currentDate.format(DateTimeFormatter.ofPattern("yyyy.MM")), fontSize = 12.sp, color = Color.Gray)
        }
        IconButton(onClick = onNextWeek) { Icon(Icons.Default.ArrowForward, contentDescription = "下一周") }
    }
}

@Composable
fun WeekHeader(mondayDate: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = SidebarWidth).height(HeaderHeight).background(Color(0xFFF5F5F5))) {
        val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val today = LocalDate.now()
        for (i in 0..6) {
            val date = mondayDate.plusDays(i.toLong())
            val isToday = date == today
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(if (isToday) Color(0xFFE3F2FD) else Color.Transparent), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(weekDays[i], fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, color = if (isToday) MaterialTheme.colorScheme.primary else Color.Black)
                Text("${date.dayOfMonth}日", fontSize = 10.sp, color = if (isToday) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        }
    }
}

@Composable
fun TimeSidebar(scrollState: ScrollState) { // 🟢 修复：现在 ScrollState 已导入，不再报错
    Column(modifier = Modifier.width(SidebarWidth).height(TotalScheduleHeight).verticalScroll(scrollState).background(Color(0xFFFAFAFA))) {
        for (i in 1..MaxNodes) {
            Box(modifier = Modifier.height(CourseHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("$i", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DrawGridLines(dayWidth: Dp) {
    Column {
        repeat(MaxNodes) {
            Spacer(modifier = Modifier.height(CourseHeight))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

// --- 本地储存工具函数 ---
private const val PREFS_NAME = "student_mate_local_data"
private const val KEY_COURSES = "course_list_json"

fun saveCoursesToLocal(context: Context, list: List<Course>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    val jsonString = Gson().toJson(list)
    editor.putString(KEY_COURSES, jsonString)
    editor.apply()
}

fun loadCoursesFromLocal(context: Context): List<Course> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_COURSES, null)
    return if (jsonString != null) {
        val type = object : TypeToken<List<Course>>() {}.type
        Gson().fromJson(jsonString, type)
    } else {
        emptyList()
    }
}
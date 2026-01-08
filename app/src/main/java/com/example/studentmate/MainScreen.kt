package com.example.studentmate

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.studentmate.screens.CountdownScreen
import com.example.studentmate.screens.ScheduleScreen
import com.example.studentmate.screens.TodoScreen

// 1. 定义页面路由结构
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "课表", Icons.Filled.DateRange)
    object Todo : Screen("todo", "待办", Icons.Filled.List)
    object Countdown : Screen("countdown", "倒数日", Icons.Filled.Notifications)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // 把页面放进一个列表，方便循环生成按钮
    val items = listOf(
        Screen.Schedule,
        Screen.Todo,
        Screen.Countdown
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 获取当前路由，为了让选中的按钮高亮显示
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // 点击时，清除栈堆，避免按返回键一直回退
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // 避免重复点击重复打开
                                launchSingleTop = true
                                // 恢复状态
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // 导航主机：决定中间显示哪个页面
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Schedule.route) { ScheduleScreen() }
            composable(Screen.Todo.route) { TodoScreen() }
            composable(Screen.Countdown.route) { CountdownScreen() }
        }
    }
}
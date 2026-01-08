package com.example.studentmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import cn.bmob.v3.Bmob
import cn.bmob.v3.BmobUser
import com.example.studentmate.screens.LoginScreen
import com.example.studentmate.ui.theme.StudentMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🟢 Bmob 初始化
        // 请确保这里的密钥是你自己的 (之前步骤中你填写的)
        Bmob.initialize(this, "c9c63fbb9dc544a80e53b282538f2b9f")

        enableEdgeToEdge()
        setContent {
            StudentMateTheme {
                // 定义一个状态变量：isLoggedIn (是否已登录)
                // 初始值通过 BmobUser.isLogin() 自动判断
                var isLoggedIn by remember { mutableStateOf(BmobUser.isLogin()) }

                if (isLoggedIn) {
                    // ✅ 如果已登录，直接显示主功能页面
                    MainScreen()
                } else {
                    // ❌ 如果没登录，显示登录/注册页
                    // 这里的 Lambda 表达式就是传给 LoginScreen 的 onLoginSuccess 回调
                    LoginScreen(onLoginSuccess = {
                        // 当登录页通知我们“成功了”，就把状态改为 true，界面会自动刷新变成 MainScreen
                        isLoggedIn = true
                    })
                }
            }
        }
    }
}
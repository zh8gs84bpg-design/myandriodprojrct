package com.example.studentmate.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
// 移除：引起报错的扩展图标导入
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
// 移除：VisualTransformation (因为我们固定隐藏密码)
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bmob.v3.BmobUser
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.LogInListener
import cn.bmob.v3.listener.SaveListener

// onLoginSuccess: 一个回调函数，当登录/注册成功时，通知 MainActivity 切换页面
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    // 状态管理
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) } // 是否正在加载中
    // 移除：passwordVisible 状态，简化逻辑确保运行

    // 获取当前上下文（用于弹窗提示 Toast）
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)), // 淡灰背景
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f) // 宽度占屏幕 85%
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 标题
                Text(
                    text = "欢迎回来 👋",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请登录或注册您的账号",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. 账号输入框
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号 / 手机号") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    // 移除：trailingIcon (小眼睛图标)，确保不报错
                    visualTransformation = PasswordVisualTransformation(), // 固定隐藏密码
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 4. 登录按钮
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true

                        // --- 修复：Bmob 登录逻辑 ---
                        // 改回使用实例方法 login() + SaveListener，这是最稳定的写法，不会报类型错误
                        val user = BmobUser()
                        user.username = username
                        user.setPassword(password)
                        user.login(object : SaveListener<BmobUser>() {
                            override fun done(bmobUser: BmobUser?, e: BmobException?) {
                                isLoading = false
                                if (e == null) {
                                    Toast.makeText(context, "登录成功！", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess() // 通知跳转
                                } else {
                                    Toast.makeText(context, "登录失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading // 加载时不可点击
                ) {
                    if (isLoading) {
                        // 修复：CircularProgressIndicator 在 Material3 中没有 size 参数，必须用 modifier
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("处理中...")
                    } else {
                        Text("登 录", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 注册按钮 (文字按钮)
                TextButton(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "注册需要填写账号和密码", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isLoading = true
                        // --- Bmob 注册逻辑 ---
                        val user = BmobUser()
                        user.username = username
                        user.setPassword(password)
                        user.signUp(object : SaveListener<BmobUser>() {
                            override fun done(bmobUser: BmobUser?, e: BmobException?) {
                                isLoading = false
                                if (e == null) {
                                    Toast.makeText(context, "注册成功，已自动登录", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess() // 注册成功也直接跳转
                                } else {
                                    // 常见错误：202=用户名已存在
                                    val msg = if (e.errorCode == 202) "账号已存在，请直接登录" else e.message
                                    Toast.makeText(context, "注册失败: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        })
                    },
                    enabled = !isLoading
                ) {
                    Text("还没有账号？点击注册", color = Color.Gray)
                }
            }
        }
    }
}
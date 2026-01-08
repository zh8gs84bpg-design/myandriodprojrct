package com.example.studentmate.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// 使用已有的Course类

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImportCourseScreen(
    onDismiss: () -> Unit,
    onDataExtracted: (List<Course>) -> Unit
) {
    // 目标登录网址
    val loginUrl = "https://jwxt.whut.edu.cn/jwapp/sys/jjsrzfwapp/dblLogin/main.do"
    val context = LocalContext.current
    
    // 用于控制 WebView
    var webView: WebView? by remember { mutableStateOf(null) }
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { extractCourseData(context, webView, onDataExtracted) }) {
                Icon(Icons.Default.Check, contentDescription = "提取课表")
            }
        }
    ) {
        // 浏览器窗口
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36"
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, pageUrl, favicon)
                            Log.d("HTML_DEBUG", "页面开始加载: $pageUrl")
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            Log.d("HTML_DEBUG", "页面加载完成: $pageUrl")
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            Log.e("HTML_DEBUG", "网络错误: ${error?.description}")
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // 所有链接在当前WebView中打开
                            request?.url?.toString()?.let { 
                                Log.d("HTML_DEBUG", "拦截链接: $it")
                                view?.loadUrl(it)
                            }
                            return true
                        }
                    }

                    loadUrl(loginUrl)
                    webView = this
                }
            },
            update = {
                if (it.url != loginUrl) {
                    it.loadUrl(loginUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(it) // 应用Scaffold的content padding
        )
    }
}

/**
 * 提取课程数据的核心逻辑
 */
private fun extractCourseData(
    context: android.content.Context,
    webView: WebView?,
    onDataExtracted: (List<Course>) -> Unit
) {
    // 【步骤1】确认按钮点击
    Log.d("HTML_DEBUG", "按钮点击，开始注入JS...")
    
    // 注入抓取脚本
    val jsCode = """
        (function() {
            // 1. 优先查找kcb_container
            let targetElement = document.getElementById('kcb_container');
            if (targetElement && targetElement.innerHTML.length > 100) {
                return targetElement.innerHTML;
            }
            
            // 2. 次级查找wut_table
            targetElement = document.querySelector('.wut_table');
            if (targetElement) {
                return targetElement.outerHTML;
            }
            
            // 3. Iframe穿透
            const iframes = document.querySelectorAll('iframe');
            for (let i = 0; i < iframes.length; i++) {
                try {
                    const iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
                    // 检查iframe中的kcb_container
                    let iframeTarget = iframeDoc.getElementById('kcb_container');
                    if (iframeTarget && iframeTarget.innerHTML.length > 100) {
                        return iframeTarget.innerHTML;
                    }
                    // 检查iframe中的wut_table
                    iframeTarget = iframeDoc.querySelector('.wut_table');
                    if (iframeTarget) {
                        return iframeTarget.outerHTML;
                    }
                } catch (e) {
                    // 跨域异常，忽略
                }
            }
            
            // 4. 最终兜底
            return 'TAG:BODY_FALLBACK' + document.body.innerHTML;
        })();
    """
    
    // 使用evaluateJavascript获取结果
    webView?.evaluateJavascript(jsCode) { html ->
        // 【步骤2】JS执行完成，处理返回数据
        Log.d("HTML_DEBUG", "收到数据长度: " + html?.length)
        
        if (!html.isNullOrEmpty()) {
            // 移除HTML字符串中的转义字符
            val cleanHtml = html.replace("\\u003C", "<").replace("\\\"", "\"")
            
            // 【步骤3】严格内容校验
            if (!isValidCoursePage(cleanHtml)) {
                // 不在课表页面，弹出提示，禁止跳转
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "未检测到课表数据，请先进入【学生课表】页面", Toast.LENGTH_LONG).show()
                }
                return@evaluateJavascript
            }
            
            // 【步骤4】在后台线程解析HTML
            runBlocking(Dispatchers.Default) {
                try {
                    val courses = parseHtml(cleanHtml)
                    
                    // 【步骤5】只有解析到课程才跳转
                    if (courses.isNotEmpty()) {
                        Log.d("HTML_DEBUG", "解析成功，共 ${courses.size} 门课程")
                        
                        // 切换到主线程执行回调和跳转
                        Handler(Looper.getMainLooper()).post {
                            onDataExtracted(courses)
                        }
                    } else {
                        // 解析到0门课程，弹出提示
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "未检测到课表数据，请先进入【学生课表】页面", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HTML_DEBUG", "解析失败: ${e.message}")
                    // 解析失败，弹出提示
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "课表解析失败，请重试", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // 未收到数据，弹出提示
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "未获取到网页内容，请重试", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * 严格内容校验：检查HTML是否包含课表相关内容
 */
private fun isValidCoursePage(html: String): Boolean {
    val lowerHtml = html.toLowerCase()
    return lowerHtml.contains("data-week") || 
           lowerHtml.contains("wut_table") || 
           lowerHtml.contains("学生课表") ||
           lowerHtml.contains("课程表")
}

/**
 * 解析HTML，提取课程数据
 */
private fun parseHtml(html: String): List<Course> {
    val courses = mutableListOf<Course>()
    
    // 预设高颜值颜色
    val courseColors = listOf(
        0xFF64B5F6.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(),
        0xFFE57373.toInt(), 0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt(), 0xFF90A4AE.toInt()
    )
    
    // 处理HTML片段
    val doc: Document = if (html.contains("<html") || html.contains("<body")) {
        Jsoup.parse(html)
    } else {
        Jsoup.parseBodyFragment(html)
    }
    
    // 直接查找带有data-week和data-begin-unit属性的td标签
    val courseTds = doc.select("td[data-week][data-begin-unit][data-end-unit]")
    
    // 调试信息
    Log.d("HTML_DEBUG", "找到td元素数量: ${courseTds.size}")
    
    for (td in courseTds) {
        try {
            // 获取定位坐标
            val week = td.attr("data-week").toInt()
            val beginUnit = td.attr("data-begin-unit").toInt()
            val endUnit = td.attr("data-end-unit").toInt()
            
            // 计算课程跨度
            val step = endUnit - beginUnit + 1
            
            // 解析课程名
            val courseNameDiv = td.selectFirst("div.mtt_item_kcmc")
            if (courseNameDiv != null) {
                // 移除内部span（如"本"标记）
                courseNameDiv.select("span").remove()
                val courseName = courseNameDiv.text().trim()
                if (courseName.isNotEmpty()) {
                    // 解析教室与周次
                    val roomText = td.selectFirst("div.mtt_item_room")?.text()?.trim() ?: ""
                    val roomInfo = parseRoomAndWeeks(roomText)
                    
                    // 分配颜色
                    val colorIndex = courses.size % courseColors.size
                    val colorInt = courseColors[colorIndex]
                    
                    // 创建课程对象
                    val course = Course(
                        name = courseName,
                        room = roomInfo.first,
                        dayOfWeek = week,
                        startNode = beginUnit,
                        step = step,
                        colorInt = colorInt
                    )
                    
                    courses.add(course)
                    
                    // 调试信息
                    Log.d("HTML_DEBUG", "解析到课程: $courseName, 星期: $week, 节次: $beginUnit-$endUnit, 教室: ${roomInfo.first}")
                }
            }
        } catch (e: Exception) {
            Log.e("HTML_DEBUG", "解析单个课程失败: ${e.message}")
            // 输出详细错误信息，包括当前td的内容
            Log.e("HTML_DEBUG", "错误TD内容: ${td.outerHtml()}")
        }
    }
    
    return courses
}

/**
 * 解析教室与周次信息
 */
private fun parseRoomAndWeeks(roomText: String): Pair<String, String> {
    // 样本数据: [1-5周, 7周, 8-17周],第1-2节,南湖校区,南湖南-博学主楼-107
    var weeks = ""
    var room = ""
    
    try {
        // 使用正则表达式提取周次信息
        val weeksPattern = Regex("\\[(.*?)\\]")
        val weeksMatch = weeksPattern.find(roomText)
        if (weeksMatch != null) {
            weeks = weeksMatch.groupValues[1]
        }
        
        // 提取教室信息
        val parts = roomText.split(",")
        if (parts.size >= 4) {
            // 格式：[周次],第X-Y节,校区,教室
            room = parts[3].trim()
        } else if (parts.size >= 3) {
            // 格式：[周次],第X-Y节,教室
            room = parts[2].trim()
        } else if (parts.isNotEmpty()) {
            // 兜底：使用最后一个部分
            room = parts.last().trim()
        }
        
        // 进一步清理教室信息，去除可能的前缀
        room = room.replace(Regex("^(南湖校区|黄家湖校区|马房山校区),"), "")
    } catch (e: Exception) {
        Log.e("HTML_DEBUG", "解析教室与周次失败: ${e.message}")
        Log.e("HTML_DEBUG", "原始文本: $roomText")
    }
    
    return Pair(room, weeks)
}

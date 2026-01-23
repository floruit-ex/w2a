package com.example.webapp
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    // 你的目标网址
    private val TARGET_URL = "https://www.wl.ax/"

    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = result.data?.data
            if (resultUri != null) {
                uploadMessage?.onReceiveValue(arrayOf(resultUri))
            } else {
                uploadMessage?.onReceiveValue(null)
            }
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "$userAgentString BusinessClientApp/1.0"
            }
            // 关键：注册 JS 接口，名字叫 "Android"
            addJavascriptInterface(JavaScriptInterface(), "Android")
        }
        setContentView(webView)

        setupWebViewClient()
        setupWebChromeClient()
        setupDownloadListener()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl(TARGET_URL)
    }

    // retryConnect 方法 ---
    inner class JavaScriptInterface {
        @JavascriptInterface
        fun getBase64FromBlobData(base64Data: String, mimeType: String) {
            convertBase64StringToPdfAndStoreIt(base64Data, mimeType)
        }

        // 给错误页面的按钮调用的
        @JavascriptInterface
        fun retryConnect() {
            runOnUiThread {
                // 重新加载目标网址
                webView.loadUrl(TARGET_URL)
            }
        }
    }

    private fun convertBase64StringToPdfAndStoreIt(base64Data: String, mimeType: String) {
        try {
            // 处理文件名
            val cleanMime = mimeType.ifEmpty { "application/octet-stream" }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime) ?: "bin"
            val fileName = "download_${System.currentTimeMillis()}.$extension"
            
            // 去掉 Base64 头部 (data:application/pdf;base64,...)
            val pureBase64 = if (base64Data.contains(",")) {
                base64Data.split(",")[1]
            } else {
                base64Data
            }
            
            val fileBytes = Base64.decode(pureBase64, Base64.DEFAULT)

            // 使用 MediaStore 保存文件 (兼容 Android 10+)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, cleanMime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                val outputStream: OutputStream? = resolver.openOutputStream(uri)
                outputStream?.use { it.write(fileBytes) }
                
                runOnUiThread {
                    Toast.makeText(this, "文件已保存: $fileName", Toast.LENGTH_LONG).show()
                }
            } else {
                throw Exception("无法创建文件 URI")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Blob 保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            // 防止跳出浏览器
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            // --- 核心拦截逻辑 ---
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // 只拦截主页面的错误 ，防止页面内某张图片加载失败也跳错误页
                if (request?.isForMainFrame == true) {
                    // 停止加载原来的丑陋报错页
                    view?.stopLoading()
                    
                    // 加载自定义的错误页
                    // 内嵌的 HTML/CSS，模仿原生 App 的断网提示
                    val customErrorPage = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                    display: flex;
                                    flex-direction: column;
                                    align-items: center;
                                    justify-content: center;
                                    height: 100vh;
                                    margin: 0;
                                    background-color: #f5f5f5;
                                    color: #333;
                                }
                                .icon {
                                    font-size: 80px;
                                    margin-bottom: 20px;
                                    color: #999;
                                }
                                h2 {
                                    margin: 0 0 10px 0;
                                    font-size: 18px;
                                    font-weight: 600;
                                }
                                p {
                                    margin: 0 0 30px 0;
                                    color: #666;
                                    font-size: 14px;
                                    text-align: center;
                                    padding: 0 20px;
                                }
                                button {
                                    background-color: #007AFF; /* 也就是蓝色的商务风 */
                                    color: white;
                                    border: none;
                                    padding: 12px 35px;
                                    border-radius: 25px;
                                    font-size: 16px;
                                    font-weight: bold;
                                    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                                    transition: background-color 0.2s;
                                }
                                button:active {
                                    background-color: #0056b3;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="icon">📡</div>
                            <h2>网络连接未就绪</h2>
                            <p>检测到手机网络异常，无法连接到业务服务器。<br>请检查您的 WiFi 或移动数据设置。</p>
                            <!-- 点击调用 Android 接口刷新 -->
                            <button onclick="window.Android.retryConnect()">重新加载</button>
                        </body>
                        </html>
                    """
                    
                    // 渲染这个 HTML
                    view?.loadDataWithBaseURL(null, customErrorPage, "text/html", "UTF-8", null)
                }
            }
        }
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    if (intent != null) fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    uploadMessage = null
                    return false
                }
                return true
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean {
                val newWebView = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(true)
                    webViewClient = WebViewClient()
                    webChromeClient = this@MainActivity.webView.webChromeClient
                }
                val dialog = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.setContentView(newWebView)
                dialog.show()
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        dialog.dismiss()
                    }
                }
                val transport = resultMsg?.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    //
    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                // 1. 处理 Blob (保持不变)
                if (url.startsWith("blob:")) {
                    val js = """
                        javascript:(function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$url', true);
                            xhr.setRequestHeader('Content-type','$mimetype');
                            xhr.responseType = 'blob';
                            xhr.onload = function(e) {
                                if (this.status == 200) {
                                    var blobReader = new FileReader();
                                    blobReader.readAsDataURL(this.response);
                                    blobReader.onloadend = function() {
                                        window.Android.getBase64FromBlobData(blobReader.result, '$mimetype');
                                    }
                                }
                            };
                            xhr.send();
                        })()
                    """
                    webView.evaluateJavascript(js, null)
                    Toast.makeText(this, "正在导出文件...", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }

               
                val isExcel = (url.contains(".xlsx", ignoreCase = true) 
                            || (contentDisposition != null && contentDisposition.contains(".xlsx", ignoreCase = true))
                            || mimetype == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            || mimetype == "application/vnd.ms-excel")

                // 如果安卓系统认为这是 text/plain，它会强行加 .txt。
                // 所以如果判定是 Excel，我们必须强制指定 Excel 的 MimeType。
                var finalMimeType = mimetype
                if (isExcel) {
                    finalMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                }

                val request = DownloadManager.Request(Uri.parse(url))
                
                // 使用篡改后的 MimeType
                request.setMimeType(finalMimeType)
                
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null) request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                var fileName = URLUtil.guessFileName(url, contentDisposition, finalMimeType)
                // 解码 URL 
                try {
                     fileName = java.net.URLDecoder.decode(fileName, "UTF-8")
                } catch (e: Exception) { }

                // 如果判定是 Excel，但文件名不是 .xlsx 结尾，强制改名
                if (isExcel && !fileName.lowercase().endsWith(".xlsx")) {
                    // 如果原名是 data.bin 或 data.txt，去掉后缀换成 .xlsx
                    if (fileName.contains(".")) {
                        fileName = fileName.substringBeforeLast(".") + ".xlsx"
                    } else {
                        fileName = "$fileName.xlsx"
                    }
                }
                
                // 清洗非法字符
                fileName = fileName.replace("[:\\\\/*?|<>]".toRegex(), "_")

                request.setDescription("正在下载表格...")
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                
                Toast.makeText(this, "正在下载：$fileName\n已保存到【下载】文件夹", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                android.util.Log.e("WebViewDownload", "下载失败: ${e.message}", e)
                Toast.makeText(this, "下载异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
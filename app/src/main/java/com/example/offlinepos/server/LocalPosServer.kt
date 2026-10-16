package com.example.offlinepos.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.offlinepos.data.local.ProductEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections

data class RemoteProductDto(
    val name: String,
    val category: String,
    val sku: String,
    val barcode: String?,
    val costPriceMinor: Long,
    val sellPriceMinor: Long,
    val stockQuantity: Long,
    val lowStockThreshold: Long,
    val unitName: String = "ခု",
    val staffName: String = "ဆိုင်ဝန်ထမ်း",
)

data class ReceivedProductLog(
    val timestampMs: Long,
    val productName: String,
    val staffName: String,
    val quantity: Long,
    val sellPriceMinor: Long,
    val isSuccess: Boolean,
)

class LocalPosServer(
    private val context: Context,
    private val port: Int = 8787,
    private val onProductReceived: suspend (RemoteProductDto) -> Pair<Boolean, String>,
    private val onGetProducts: suspend () -> List<ProductEntity>,
    private val onGetCategories: suspend () -> List<String>,
    private val getStoreName: () -> String,
) {
    private val tag = "LocalPosServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isRunning: Boolean = false
        private set

    val receivedLogs = mutableListOf<ReceivedProductLog>()

    fun start(): Boolean {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return true
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        var success = false
        // Retry binding up to 3 times with small backoff in case socket was just closed
        for (attempt in 1..3) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                serverSocket = ss
                isRunning = true
                success = true
                Log.i(tag, "Local POS Server started successfully on port $port (attempt $attempt)")
                break
            } catch (e: Exception) {
                Log.w(tag, "Bind attempt $attempt failed on port $port: ${e.message}")
                if (attempt < 3) {
                    try { Thread.sleep(200) } catch (_: Exception) {}
                }
            }
        }

        if (!success) {
            isRunning = false
            return false
        }

        serverJob?.cancel()
        serverJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleClientSocket(socket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(tag, "Error accepting connection: ${e.message}")
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        Log.i(tag, "Local POS Server stopped")
    }

    private suspend fun handleClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 15000
            val inputStream = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            // Read HTTP headers safely as raw bytes until \r\n\r\n or \n\n
            val headerStream = ByteArrayOutputStream()
            var prev3 = -1
            var prev2 = -1
            var prev1 = -1
            var b: Int
            while (true) {
                b = inputStream.read()
                if (b == -1) break
                headerStream.write(b)
                if ((prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) ||
                    (prev1 == '\n'.code && b == '\n'.code)
                ) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }

            val headerText = headerStream.toString("UTF-8")
            if (headerText.isBlank()) {
                socket.close()
                return@withContext
            }

            val headerLines = headerText.lines()
            val requestLine = headerLines.firstOrNull()?.trim() ?: ""
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return@withContext
            }

            val method = parts[0].uppercase().trim()
            val fullPath = parts[1].trim()
            val path = fullPath.substringBefore("?")

            var contentLength = 0
            for (line in headerLines) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    break
                }
            }

            // Read EXACTLY contentLength bytes from the stream
            val body = if (contentLength > 0) {
                val buffer = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = inputStream.read(buffer, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                String(buffer, 0, totalRead, Charsets.UTF_8)
            } else ""

            // Handle preflight OPTIONS
            if (method == "OPTIONS") {
                sendResponse(output, 200, "text/plain", "")
                socket.close()
                return@withContext
            }

            when {
                // Staff HTML5 Web Portal
                path == "/" || path == "/client" || path == "/index.html" -> {
                    val html = generateStaffWebPortalHtml()
                    sendResponse(output, 200, "text/html", html)
                }

                // Server Status API
                path == "/api/status" -> {
                    val products = onGetProducts()
                    val json = JSONObject().apply {
                        put("status", "online")
                        put("serverName", "15C_787_AI_SERVER")
                        put("storeName", getStoreName())
                        put("productCount", products.size)
                        put("timestamp", System.currentTimeMillis())
                    }
                    sendResponse(output, 200, "application/json", json.toString())
                }

                // Get Categories API
                path == "/api/categories" -> {
                    val categories = onGetCategories()
                    val array = JSONArray(categories)
                    sendResponse(output, 200, "application/json", array.toString())
                }

                // Get Existing Products List API
                path == "/api/products" && method == "GET" -> {
                    val products = onGetProducts()
                    val array = JSONArray()
                    products.forEach { p ->
                        val obj = JSONObject().apply {
                            put("productId", p.productId)
                            put("name", p.name)
                            put("category", p.category)
                            put("sku", p.sku)
                            put("barcode", p.barcode ?: "")
                            put("sellPriceMinor", p.sellPriceMinor)
                            put("costPriceMinor", p.costPriceMinor)
                            put("stockQuantity", p.stockQuantity)
                            put("unitName", p.unitName)
                        }
                        array.put(obj)
                    }
                    sendResponse(output, 200, "application/json", array.toString())
                }

                // Add New Product from Staff Client or Chrome Browser Web Portal
                (path == "/api/products" || path == "/api/add-product") && method == "POST" -> {
                    try {
                        val dto = parseProductPayload(body)
                        if (dto.name.isBlank()) {
                            val errJson = JSONObject().apply {
                                put("success", false)
                                put("message", "ကုန်ပစ္စည်း အမည် ထည့်သွင်းရန် လိုအပ်ပါသည်")
                            }
                            sendResponse(output, 400, "application/json", errJson.toString())
                        } else {
                            val (success, msg) = onProductReceived(dto)

                            synchronized(receivedLogs) {
                                receivedLogs.add(
                                    0,
                                    ReceivedProductLog(
                                        timestampMs = System.currentTimeMillis(),
                                        productName = dto.name,
                                        staffName = dto.staffName,
                                        quantity = dto.stockQuantity,
                                        sellPriceMinor = dto.sellPriceMinor,
                                        isSuccess = success
                                    )
                                )
                                if (receivedLogs.size > 50) receivedLogs.removeAt(receivedLogs.lastIndex)
                            }

                            val resJson = JSONObject().apply {
                                put("success", success)
                                put("message", msg)
                                put("productName", dto.name)
                            }
                            sendResponse(output, if (success) 200 else 400, "application/json", resJson.toString())
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Add product processing error: ${e.message}", e)
                        val errJson = JSONObject().apply {
                            put("success", false)
                            put("message", "အချက်အလက် ထည့်သွင်းမှု အမှားဖြစ်နေပါသည်: ${e.message}")
                        }
                        sendResponse(output, 400, "application/json", errJson.toString())
                    }
                }

                else -> {
                    val notFound = JSONObject().apply {
                        put("error", "Not Found")
                        put("path", path)
                    }
                    sendResponse(output, 404, "application/json", notFound.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Client handler exception: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun parseProductPayload(body: String): RemoteProductDto {
        val trimmed = body.trim()
        return if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val name = json.optString("name", json.optString("productName", "")).trim()
            val category = json.optString("category", "အထွေထွေ").trim().ifBlank { "အထွေထွေ" }
            val sku = json.optString("sku", "").trim().ifBlank { "PROD-${System.currentTimeMillis() % 100000}" }
            val barcode = json.optString("barcode", "").trim().ifBlank { null }
            val sellPrice = if (json.has("sellPriceMinor")) json.optLong("sellPriceMinor") else json.optLong("sellPrice", 0L)
            val costPrice = if (json.has("costPriceMinor")) json.optLong("costPriceMinor") else json.optLong("costPrice", 0L)
            val stock = if (json.has("stockQuantity")) json.optLong("stockQuantity", 1L) else json.optLong("stock", 1L)
            val threshold = if (json.has("lowStockThreshold")) json.optLong("lowStockThreshold", 3L) else json.optLong("threshold", 3L)
            val unit = json.optString("unitName", json.optString("unit", "ခု")).trim().ifBlank { "ခု" }
            val staff = json.optString("staffName", json.optString("staff", "ဆိုင်ဝန်ထမ်း")).trim().ifBlank { "ဆိုင်ဝန်ထမ်း" }

            RemoteProductDto(
                name = name,
                category = category,
                sku = sku,
                barcode = barcode,
                costPriceMinor = costPrice,
                sellPriceMinor = sellPrice,
                stockQuantity = stock,
                lowStockThreshold = threshold,
                unitName = unit,
                staffName = staff
            )
        } else {
            val params = mutableMapOf<String, String>()
            trimmed.split("&").forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.isNotEmpty()) {
                    val key = URLDecoder.decode(kv[0], "UTF-8")
                    val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                    params[key] = value
                }
            }
            val name = (params["name"] ?: params["productName"] ?: "").trim()
            val category = (params["category"] ?: "အထွေထွေ").trim().ifBlank { "အထွေထွေ" }
            val sku = (params["sku"] ?: "").trim().ifBlank { "PROD-${System.currentTimeMillis() % 100000}" }
            val barcode = (params["barcode"] ?: "").trim().ifBlank { null }
            val sellPrice = (params["sellPriceMinor"] ?: params["sellPrice"] ?: "0").toLongOrNull() ?: 0L
            val costPrice = (params["costPriceMinor"] ?: params["costPrice"] ?: "0").toLongOrNull() ?: 0L
            val stock = (params["stockQuantity"] ?: params["stock"] ?: "1").toLongOrNull() ?: 1L
            val threshold = (params["lowStockThreshold"] ?: params["threshold"] ?: "3").toLongOrNull() ?: 3L
            val unit = (params["unitName"] ?: params["unit"] ?: "ခု").trim().ifBlank { "ခု" }
            val staff = (params["staffName"] ?: params["staff"] ?: "ဆိုင်ဝန်ထမ်း").trim().ifBlank { "ဆိုင်ဝန်ထမ်း" }

            RemoteProductDto(
                name = name,
                category = category,
                sku = sku,
                barcode = barcode,
                costPriceMinor = costPrice,
                sellPriceMinor = sellPrice,
                stockQuantity = stock,
                lowStockThreshold = threshold,
                unitName = unit,
                staffName = staff
            )
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Response"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            // fallback
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    fun getServerUrl(): String {
        return "http://${getLocalIpAddress()}:$port"
    }

    private fun generateStaffWebPortalHtml(): String {
        val store = getStoreName()
        return """
<!DOCTYPE html>
<html lang="my">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>787 ဆိုင်ဝန်ထမ်း Web Portal (15C)</title>
    <style>
        :root {
            --primary: #0284c7;
            --primary-dark: #0369a1;
            --success: #16a34a;
            --bg: #f8fafc;
            --card: #ffffff;
            --text: #0f172a;
            --text-muted: #64748b;
            --border: #cbd5e1;
            --chip-bg: #e2e8f0;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Pyidaungsu", sans-serif; }
        body { background: var(--bg); color: var(--text); padding: 12px; line-height: 1.5; max-width: 600px; margin: 0 auto; }
        .header { background: linear-gradient(135deg, #0369a1, #0f172a); color: white; padding: 16px; border-radius: 14px; margin-bottom: 12px; text-align: center; box-shadow: 0 4px 12px rgba(2,132,199,0.2); }
        .header h1 { font-size: 19px; font-weight: bold; margin-bottom: 4px; }
        .header p { font-size: 12px; opacity: 0.9; }
        .status-badge { display: inline-block; background: #22c55e; color: white; padding: 3px 12px; border-radius: 12px; font-size: 11px; font-weight: bold; margin-top: 8px; }
        .net-status-bar { display: flex; justify-content: center; gap: 14px; margin-top: 8px; font-size: 11px; }
        .net-status-item { display: inline-flex; align-items: center; gap: 5px; background: rgba(255,255,255,0.15); padding: 2px 8px; border-radius: 8px; }
        .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
        .dot-green { background: #22c55e; box-shadow: 0 0 6px rgba(34,197,94,0.8); }
        .dot-blue { background: #38bdf8; box-shadow: 0 0 6px rgba(56,189,248,0.8); }
        .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); margin-bottom: 12px; }
        .card h2 { font-size: 15px; font-weight: bold; color: var(--primary-dark); margin-bottom: 12px; border-bottom: 1px solid #f1f5f9; padding-bottom: 6px; }
        .form-group { margin-bottom: 12px; }
        label { display: block; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; }
        input, select { width: 100%; min-height: 44px; padding: 8px 12px; border: 1.5px solid var(--border); border-radius: 8px; font-size: 14px; background: white; outline: none; transition: border-color 0.2s; }
        input:focus, select:focus { border-color: var(--primary); }
        .row { display: flex; gap: 8px; }
        .row .form-group { flex: 1; }
        .btn { width: 100%; min-height: 48px; padding: 12px; background: var(--primary); color: white; border: none; border-radius: 10px; font-size: 15px; font-weight: bold; cursor: pointer; transition: background 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px; }
        .btn:hover { background: var(--primary-dark); }
        .btn:disabled { background: #cbd5e1; cursor: not-allowed; }
        .btn-scan { background: #3b82f6; color: white; border: none; border-radius: 8px; padding: 0 12px; font-size: 13px; font-weight: bold; cursor: pointer; display: flex; align-items: center; gap: 4px; white-space: nowrap; }
        .chips-container { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; margin-bottom: 8px; }
        .chip { background: var(--chip-bg); color: var(--text); padding: 4px 10px; border-radius: 14px; font-size: 11px; font-weight: 600; cursor: pointer; border: 1px solid #cbd5e1; }
        .chip:hover { background: #bae6fd; color: #0369a1; }
        .qty-btns { display: flex; gap: 6px; margin-top: 4px; }
        .qty-btn { flex: 1; background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd; padding: 6px; border-radius: 6px; font-size: 12px; font-weight: bold; cursor: pointer; text-align: center; }
        .qty-btn:hover { background: #bae6fd; }
        .alert { padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 12px; display: none; font-weight: 600; }
        .alert-success { background: #dcfce7; color: #15803d; border: 1px solid #86efac; }
        .alert-error { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
        .recent-item { padding: 10px; border-bottom: 1px solid #f1f5f9; font-size: 13px; display: flex; justify-content: space-between; align-items: center; }
        .recent-item:last-child { border-bottom: none; }
        .badge { background: #e0f2fe; color: #0284c7; padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: bold; }
        #scannerBox { display: none; margin-bottom: 12px; border-radius: 12px; overflow: hidden; position: relative; background: #000; }
        #scannerVideo { width: 100%; height: 220px; object-fit: cover; }
        .scanner-overlay { position: absolute; inset: 20% 15%; border: 2px dashed #22c55e; border-radius: 8px; pointer-events: none; }
        .scanner-close { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,0.6); color: white; border: none; padding: 4px 10px; border-radius: 6px; font-size: 12px; cursor: pointer; }
    </style>
</head>
<body>
    <div class="header">
        <h1>📱 $store</h1>
        <p>Direct HTML5 Web Portal (15C_787_AI)</p>
        <span class="status-badge" id="serverStatusBadge">🟢 15C Main Server ချိတ်ဆက်ပြီး</span>
        <div class="net-status-bar">
            <span class="net-status-item"><span class="dot dot-green"></span> Server: Reachable</span>
            <span class="net-status-item"><span class="dot dot-blue"></span> DB: Synced</span>
        </div>
    </div>

    <div id="alertSuccess" class="alert alert-success"></div>
    <div id="alertError" class="alert alert-error"></div>

    <div class="card">
        <h2>📦 ကုန်ပစ္စည်းအသစ် ထည့်သွင်းရန်</h2>
        <form id="productForm">
            <div class="form-group">
                <label>👤 ထည့်သွင်းသူ ဝန်ထမ်းအမည် *</label>
                <input type="text" id="staffName" required placeholder="ဥပမာ - ကိုမင်း / ဝန်ထမ်း ၁">
            </div>

            <div class="form-group">
                <label>🏷️ ကုန်ပစ္စည်း အမည် *</label>
                <input type="text" id="productName" required placeholder="ဥပမာ - ဆင်တံဆိပ် ဘိလပ်မြေ (၅၀ ကီလို)">
            </div>

            <div class="row">
                <div class="form-group">
                    <label>📂 အမျိုးအစား (Category)</label>
                    <input type="text" id="category" list="categoryDatalist" value="အထွေထွေ" placeholder="ရွေးချယ်ပါ သို့မဟုတ် ရိုက်ထည့်ပါ">
                    <datalist id="categoryDatalist"></datalist>
                </div>
                <div class="form-group">
                    <label>📦 ယူနစ် (Unit)</label>
                    <input type="text" id="unitName" value="ခု" placeholder="ခု / အိတ် / ထုပ်">
                </div>
            </div>

            <div class="chips-container" id="categoryChips"></div>
            <div class="chips-container">
                <span class="chip" onclick="setUnit('ခု')">ခု</span>
                <span class="chip" onclick="setUnit('အိတ်')">အိတ်</span>
                <span class="chip" onclick="setUnit('ထုပ်')">ထုပ်</span>
                <span class="chip" onclick="setUnit('ဘူး')">ဘူး</span>
                <span class="chip" onclick="setUnit('ပေ')">ပေ</span>
                <span class="chip" onclick="setUnit('ကီလို')">ကီလို</span>
            </div>

            <!-- Barcode Scanner Box -->
            <div id="scannerBox">
                <video id="scannerVideo" playsinline></video>
                <div class="scanner-overlay"></div>
                <button type="button" class="scanner-close" onclick="stopBarcodeScanner()">✕ ပိတ်မည်</button>
            </div>

            <div class="row">
                <div class="form-group">
                    <label>🔢 SKU နံပါတ်</label>
                    <input type="text" id="sku" placeholder="အလိုအလျောက် ထုတ်ပေးမည်">
                </div>
                <div class="form-group">
                    <label>📷 Barcode နံပါတ်</label>
                    <div style="display:flex; gap:6px;">
                        <input type="text" id="barcode" placeholder="Barcode ရိုက်ပါ">
                        <button type="button" class="btn-scan" onclick="startBarcodeScanner()">📷 Scan</button>
                    </div>
                </div>
            </div>

            <div class="row">
                <div class="form-group">
                    <label>💰 ရောင်းဈေး (Ks) *</label>
                    <input type="number" id="sellPrice" required placeholder="ဥပမာ - 15000">
                </div>
                <div class="form-group">
                    <label>💵 ဝယ်ရင်းဈေး (Ks)</label>
                    <input type="number" id="costPrice" value="0" placeholder="ဥပမာ - 12000">
                </div>
            </div>

            <div class="row">
                <div class="form-group">
                    <label>📦 လက်ကျန် အရေအတွက် *</label>
                    <input type="number" id="stockQuantity" value="10" required placeholder="ဥပမာ - 50">
                    <div class="qty-btns">
                        <span class="qty-btn" onclick="addQty(1)">+1</span>
                        <span class="qty-btn" onclick="addQty(5)">+5</span>
                        <span class="qty-btn" onclick="addQty(10)">+10</span>
                        <span class="qty-btn" onclick="addQty(50)">+50</span>
                    </div>
                </div>
                <div class="form-group">
                    <label>⚠️ သတိပေး လက်ကျန်</label>
                    <input type="number" id="lowStockThreshold" value="3" placeholder="3">
                </div>
            </div>

            <button type="submit" id="submitBtn" class="btn">🚀 Main Server သို့ ပေါင်းထည့်မည်</button>
        </form>
    </div>

    <div class="card">
        <h2>📋 ယခု ထည့်သွင်းပြီးသော ပစ္စည်းများ</h2>
        <div id="recentList">
            <p style="text-align: center; color: var(--text-muted); font-size: 12px; padding: 10px;">ပစ္စည်း အသစ်များ မရှိသေးပါ</p>
        </div>
    </div>

    <script>
        const form = document.getElementById('productForm');
        const submitBtn = document.getElementById('submitBtn');
        const alertSuccess = document.getElementById('alertSuccess');
        const alertError = document.getElementById('alertError');
        const recentList = document.getElementById('recentList');
        const staffNameInput = document.getElementById('staffName');
        const categoryDatalist = document.getElementById('categoryDatalist');
        const categoryChips = document.getElementById('categoryChips');
        let addedItems = [];

        // Load saved staff name
        const savedStaff = localStorage.getItem('787_staff_name');
        if (savedStaff) {
            staffNameInput.value = savedStaff;
        } else {
            staffNameInput.value = 'ဆိုင်ဝန်ထမ်း #1';
        }

        staffNameInput.addEventListener('change', function() {
            localStorage.setItem('787_staff_name', staffNameInput.value.trim());
        });

        // Load categories on start
        fetch('/api/categories')
            .then(function(res) { return res.json(); })
            .then(function(cats) {
                if (Array.isArray(cats) && cats.length > 0) {
                    categoryDatalist.innerHTML = cats.map(function(c) {
                        return '<option value="' + c + '">';
                    }).join('');

                    categoryChips.innerHTML = cats.slice(0, 8).map(function(c) {
                        return '<span class="chip" onclick="setCategory(\'' + c + '\')">' + c + '</span>';
                    }).join('');
                }
            })
            .catch(function(e) { console.log('Categories load error:', e); });

        function setCategory(cat) {
            document.getElementById('category').value = cat;
        }

        function setUnit(unit) {
            document.getElementById('unitName').value = unit;
        }

        function addQty(n) {
            const el = document.getElementById('stockQuantity');
            el.value = (parseInt(el.value) || 0) + n;
        }

        // Camera Barcode Scanning using BarcodeDetector API
        let videoStream = null;
        let barcodeDetector = null;
        if ('BarcodeDetector' in window) {
            try {
                barcodeDetector = new BarcodeDetector({
                    formats: ['code_128', 'ean_13', 'ean_8', 'qr_code', 'upc_a', 'upc_e']
                });
            } catch (e) {
                barcodeDetector = null;
            }
        }

        function playSuccessTone() {
            try {
                const AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (AudioCtx) {
                    const ctx = new AudioCtx();
                    const osc = ctx.createOscillator();
                    const g = ctx.createGain();
                    osc.connect(g);
                    g.connect(ctx.destination);
                    osc.frequency.setValueAtTime(880, ctx.currentTime);
                    g.gain.setValueAtTime(0.2, ctx.currentTime);
                    g.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.2);
                    osc.start();
                    osc.stop(ctx.currentTime + 0.2);
                }
            } catch (e) {}
        }

        function startBarcodeScanner() {
            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                const scannerBox = document.getElementById('scannerBox');
                const video = document.getElementById('scannerVideo');
                scannerBox.style.display = 'block';

                navigator.mediaDevices.getUserMedia({
                    video: { facingMode: 'environment' }
                }).then(function(stream) {
                    videoStream = stream;
                    video.srcObject = stream;
                    video.play();
                    requestAnimationFrame(scanVideoFrame);
                }).catch(function(err) {
                    alert('ကင်မရာ ဖွင့်မရပါ: ' + err.message + '\nBarcode ကို အောက်ပါအကွက်တွင် တိုက်ရိုက် ရိုက်ထည့်နိုင်ပါသည်');
                    scannerBox.style.display = 'none';
                    document.getElementById('barcode').focus();
                });
            } else {
                alert('သတိပေးချက်: ဖုန်း Chrome HTTP ချိတ်ဆက်မှုတွင် ကင်မရာကို တိုက်ရိုက်မဖွင့်နိုင်ပါက Barcode နံပါတ်ကို တိုက်ရိုက် ရိုက်ထည့်နိုင်ပါသည်');
                document.getElementById('barcode').focus();
            }
        }

        function scanVideoFrame() {
            if (!videoStream) return;
            const video = document.getElementById('scannerVideo');
            if (barcodeDetector && video.readyState === video.HAVE_ENOUGH_DATA) {
                barcodeDetector.detect(video).then(function(barcodes) {
                    if (barcodes && barcodes.length > 0) {
                        document.getElementById('barcode').value = barcodes[0].rawValue;
                        stopBarcodeScanner();
                        playSuccessTone();
                        if (navigator.vibrate) navigator.vibrate(80);
                        return;
                    }
                    if (videoStream) requestAnimationFrame(scanVideoFrame);
                }).catch(function() {
                    if (videoStream) requestAnimationFrame(scanVideoFrame);
                });
            } else {
                requestAnimationFrame(scanVideoFrame);
            }
        }

        function stopBarcodeScanner() {
            if (videoStream) {
                videoStream.getTracks().forEach(function(t) { t.stop(); });
                videoStream = null;
            }
            document.getElementById('scannerBox').style.display = 'none';
        }

        form.addEventListener('submit', async function(e) {
            e.preventDefault();
            alertSuccess.style.display = 'none';
            alertError.style.display = 'none';
            submitBtn.disabled = true;
            submitBtn.innerText = 'ခေတ္တစောင့်ပါ...';

            localStorage.setItem('787_staff_name', staffNameInput.value.trim());

            const payload = {
                staffName: staffNameInput.value.trim() || 'ဆိုင်ဝန်ထမ်း #1',
                name: document.getElementById('productName').value.trim(),
                category: document.getElementById('category').value.trim() || 'အထွေထွေ',
                unitName: document.getElementById('unitName').value.trim() || 'ခု',
                sku: document.getElementById('sku').value.trim(),
                barcode: document.getElementById('barcode').value.trim(),
                sellPriceMinor: parseInt(document.getElementById('sellPrice').value) || 0,
                costPriceMinor: parseInt(document.getElementById('costPrice').value) || 0,
                stockQuantity: parseInt(document.getElementById('stockQuantity').value) || 0,
                lowStockThreshold: parseInt(document.getElementById('lowStockThreshold').value) || 3
            };

            try {
                const res = await fetch('/api/products', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                if (res.ok && data.success) {
                    playSuccessTone();
                    if (navigator.vibrate) navigator.vibrate([100, 50, 100]);

                    alertSuccess.innerText = '✅ ' + (data.message || 'Main Server သို့ အောင်မြင်စွာ ပေါင်းထည့်ပြီးပါပြီ');
                    alertSuccess.style.display = 'block';

                    addedItems.unshift({
                        name: payload.name,
                        price: payload.sellPriceMinor,
                        stock: payload.stockQuantity,
                        time: new Date().toLocaleTimeString()
                    });
                    renderRecent();

                    // reset inputs
                    document.getElementById('productName').value = '';
                    document.getElementById('sku').value = '';
                    document.getElementById('barcode').value = '';
                    document.getElementById('sellPrice').value = '';
                    document.getElementById('costPrice').value = '0';
                    document.getElementById('stockQuantity').value = '10';
                    document.getElementById('productName').focus();
                } else {
                    alertError.innerText = '❌ ' + (data.message || 'ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
                    alertError.style.display = 'block';
                }
            } catch (err) {
                alertError.innerText = '❌ ဆာဗာသို့ ချိတ်ဆက်၍ မရပါ: ' + err.message;
                alertError.style.display = 'block';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerText = '🚀 Main Server သို့ ပေါင်းထည့်မည်';
            }
        });

        function renderRecent() {
            if (addedItems.length === 0) return;
            recentList.innerHTML = addedItems.map(function(item) {
                return '<div class="recent-item">' +
                    '<div><strong>' + item.name + '</strong><br>' +
                    '<span style="font-size: 11px; color: var(--text-muted);">' + item.time + ' • လက်ကျန်: ' + item.stock + ' ခု</span></div>' +
                    '<span class="badge">' + Number(item.price).toLocaleString() + ' Ks</span></div>';
            }).join('');
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}

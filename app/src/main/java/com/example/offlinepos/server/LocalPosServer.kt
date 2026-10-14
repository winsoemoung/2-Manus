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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
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

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            isRunning = true
            Log.i(tag, "Local POS Server started on port $port")

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
        } catch (e: Exception) {
            Log.e(tag, "Failed to start server: ${e.message}", e)
            isRunning = false
        }
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
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: run {
                socket.close()
                return@withContext
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return@withContext
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buffer, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                String(buffer, 0, totalRead)
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
                        put("serverName", "13D_787_AI_SERVER")
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

                // Add New Product from Staff Client
                path == "/api/products" && method == "POST" -> {
                    try {
                        val json = JSONObject(body)
                        val name = json.optString("name", "").trim()
                        val category = json.optString("category", "အထွေထွေ").trim().ifBlank { "အထွေထွေ" }
                        val sku = json.optString("sku", "").trim().ifBlank { "PROD-${System.currentTimeMillis() % 100000}" }
                        val barcode = json.optString("barcode", "").trim().ifBlank { null }
                        val sellPrice = json.optLong("sellPriceMinor", 0L)
                        val costPrice = json.optLong("costPriceMinor", 0L)
                        val stock = json.optLong("stockQuantity", 0L)
                        val threshold = json.optLong("lowStockThreshold", 3L)
                        val unit = json.optString("unitName", "ခု").trim().ifBlank { "ခု" }
                        val staff = json.optString("staffName", "ဆိုင်ဝန်ထမ်း").trim().ifBlank { "ဆိုင်ဝန်ထမ်း" }

                        if (name.isBlank()) {
                            val errJson = JSONObject().apply {
                                put("success", false)
                                put("message", "ကုန်ပစ္စည်း အမည် ထည့်သွင်းရန် လိုအပ်ပါသည်")
                            }
                            sendResponse(output, 400, "application/json", errJson.toString())
                        } else {
                            val dto = RemoteProductDto(
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
                            val (success, msg) = onProductReceived(dto)

                            synchronized(receivedLogs) {
                                receivedLogs.add(
                                    0,
                                    ReceivedProductLog(
                                        timestampMs = System.currentTimeMillis(),
                                        productName = name,
                                        staffName = staff,
                                        quantity = stock,
                                        sellPriceMinor = sellPrice,
                                        isSuccess = success
                                    )
                                )
                                if (receivedLogs.size > 50) receivedLogs.removeAt(receivedLogs.lastIndex)
                            }

                            val resJson = JSONObject().apply {
                                put("success", success)
                                put("message", msg)
                                put("productName", name)
                            }
                            sendResponse(output, if (success) 200 else 400, "application/json", resJson.toString())
                        }
                    } catch (e: Exception) {
                        val errJson = JSONObject().apply {
                            put("success", false)
                            put("message", "JSON Data အမှားဖြစ်နေပါသည်: ${e.message}")
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
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
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
    <title>787 ဆိုင်ဝန်ထမ်း ကုန်ပစ္စည်းထည့်စနစ်</title>
    <style>
        :root {
            --primary: #0284c7;
            --primary-dark: #0369a1;
            --success: #16a34a;
            --bg: #f8fafc;
            --card: #ffffff;
            --text: #0f172a;
            --text-muted: #64748b;
            --border: #e2e8f0;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Pyidaungsu", sans-serif; }
        body { background: var(--bg); color: var(--text); padding: 14px; line-height: 1.5; }
        .header { background: linear-gradient(135deg, #0284c7, #0f172a); color: white; padding: 16px; border-radius: 14px; margin-bottom: 16px; text-align: center; box-shadow: 0 4px 12px rgba(2,132,199,0.18); }
        .header h1 { font-size: 18px; font-weight: bold; margin-bottom: 4px; }
        .header p { font-size: 12px; opacity: 0.9; }
        .status-badge { display: inline-block; background: #22c55e; color: white; padding: 2px 10px; border-radius: 12px; font-size: 11px; font-weight: bold; margin-top: 6px; }
        .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); margin-bottom: 16px; }
        .card h2 { font-size: 15px; font-weight: bold; color: var(--primary-dark); margin-bottom: 12px; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
        .form-group { margin-bottom: 12px; }
        label { display: block; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; }
        input, select { width: 100%; padding: 10px 12px; border: 1.5px solid var(--border); border-radius: 8px; font-size: 14px; background: white; outline: none; transition: border-color 0.2s; }
        input:focus, select:focus { border-color: var(--primary); }
        .row { display: flex; gap: 8px; }
        .row .form-group { flex: 1; }
        .btn { width: 100%; padding: 12px; background: var(--primary); color: white; border: none; border-radius: 10px; font-size: 15px; font-weight: bold; cursor: pointer; transition: background 0.2s; }
        .btn:hover { background: var(--primary-dark); }
        .btn:disabled { background: #cbd5e1; cursor: not-allowed; }
        .alert { padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 12px; display: none; font-weight: 600; }
        .alert-success { background: #dcfce7; color: #15803d; border: 1px solid #86efac; }
        .alert-error { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
        .recent-item { padding: 10px; border-bottom: 1px solid var(--border); font-size: 13px; display: flex; justify-content: space-between; align-items: center; }
        .recent-item:last-child { border-bottom: none; }
        .badge { background: #e0f2fe; color: #0284c7; padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: bold; }
    </style>
</head>
<body>
    <div class="header">
        <h1>📱 $store</h1>
        <p>ဆိုင်ဝန်ထမ်း ကုန်ပစ္စည်းအသစ် ထည့်သွင်းခြင်း စနစ်</p>
        <span class="status-badge">🟢 Main Server ချိတ်ဆက်ပြီး (13D)</span>
    </div>

    <div id="alertSuccess" class="alert alert-success"></div>
    <div id="alertError" class="alert alert-error"></div>

    <div class="card">
        <h2>📦 ကုန်ပစ္စည်းအသစ် ထည့်သွင်းရန်</h2>
        <form id="productForm">
            <div class="form-group">
                <label>👤 ထည့်သွင်းသူ ဝန်ထမ်းအမည်</label>
                <input type="text" id="staffName" value="ဆိုင်ဝန်ထမ်း #1" required placeholder="ဥပမာ - ကိုမင်း">
            </div>

            <div class="form-group">
                <label>🏷️ ကုန်ပစ္စည်း အမည် *</label>
                <input type="text" id="productName" required placeholder="ဥပမာ - ဆင်တံဆိပ် ဘိလပ်မြေ (၅၀ ကီလို)">
            </div>

            <div class="row">
                <div class="form-group">
                    <label>📂 အမျိုးအစား</label>
                    <input type="text" id="category" value="အထွေထွေ" placeholder="ဥပမာ - ဘိလပ်မြေ">
                </div>
                <div class="form-group">
                    <label>📦 ယူနစ်</label>
                    <input type="text" id="unitName" value="ခု" placeholder="ခု / အိတ် / ထုပ်">
                </div>
            </div>

            <div class="row">
                <div class="form-group">
                    <label>🔢 SKU နံပါတ်</label>
                    <input type="text" id="sku" placeholder="အလိုအလျောက် သတ်မှတ်မည်">
                </div>
                <div class="form-group">
                    <label>📷 Barcode နံပါတ်</label>
                    <input type="text" id="barcode" placeholder="Barcode ရိုက်ထည့်ပါ">
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
        const addedItems = [];

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            alertSuccess.style.display = 'none';
            alertError.style.display = 'none';
            submitBtn.disabled = true;
            submitBtn.innerText = 'ခေတ္တစောင့်ပါ...';

            const payload = {
                staffName: document.getElementById('staffName').value.trim(),
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
                    alertSuccess.innerText = '✅ ' + (data.message || 'Main Server သို့ အောင်မြင်စွာ ပေါင်းထည့်ပြီးပါပြီ');
                    alertSuccess.style.display = 'block';

                    addedItems.unshift({
                        name: payload.name,
                        price: payload.sellPriceMinor,
                        stock: payload.stockQuantity,
                        time: new Date().toLocaleTimeString()
                    });
                    renderRecent();

                    // reset form
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

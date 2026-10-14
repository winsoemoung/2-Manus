package com.example.offlinepos.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ServerStatusInfo(
    val status: String,
    val serverName: String,
    val storeName: String,
    val productCount: Int,
)

data class RemoteProductItem(
    val productId: Long,
    val name: String,
    val category: String,
    val sku: String,
    val barcode: String,
    val sellPriceMinor: Long,
    val costPriceMinor: Long,
    val stockQuantity: Long,
    val unitName: String,
)

object StaffClientApi {

    suspend fun testConnection(baseUrl: String): Result<ServerStatusInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trimEnd('/') + "/api/status"
            val url = URL(cleanUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val response = reader.readText()
                val json = JSONObject(response)
                val info = ServerStatusInfo(
                    status = json.optString("status", "online"),
                    serverName = json.optString("serverName", "787_POS_SERVER"),
                    storeName = json.optString("storeName", "san thit"),
                    productCount = json.optInt("productCount", 0)
                )
                Result.success(info)
            } else {
                Result.failure(Exception("ဆာဗာ တုံ့ပြန်မှု ကုဒ်: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCategories(baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trimEnd('/') + "/api/categories"
            val url = URL(cleanUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val response = reader.readText()
                val array = JSONArray(response)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Error code: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProducts(baseUrl: String): Result<List<RemoteProductItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trimEnd('/') + "/api/products"
            val url = URL(cleanUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val response = reader.readText()
                val array = JSONArray(response)
                val list = mutableListOf<RemoteProductItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        RemoteProductItem(
                            productId = obj.optLong("productId"),
                            name = obj.optString("name"),
                            category = obj.optString("category"),
                            sku = obj.optString("sku"),
                            barcode = obj.optString("barcode"),
                            sellPriceMinor = obj.optLong("sellPriceMinor"),
                            costPriceMinor = obj.optLong("costPriceMinor"),
                            stockQuantity = obj.optLong("stockQuantity"),
                            unitName = obj.optString("unitName", "ခု")
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Error code: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendProduct(baseUrl: String, product: RemoteProductDto): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trimEnd('/') + "/api/products"
            val url = URL(cleanUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            val json = JSONObject().apply {
                put("name", product.name)
                put("category", product.category)
                put("sku", product.sku)
                put("barcode", product.barcode ?: "")
                put("costPriceMinor", product.costPriceMinor)
                put("sellPriceMinor", product.sellPriceMinor)
                put("stockQuantity", product.stockQuantity)
                put("lowStockThreshold", product.lowStockThreshold)
                put("unitName", product.unitName)
                put("staffName", product.staffName)
            }

            val bodyBytes = json.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os ->
                os.write(bodyBytes)
                os.flush()
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val respString = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val resJson = try { JSONObject(respString) } catch (e: Exception) { null }
            val message = resJson?.optString("message") ?: if (isSuccess) "အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ" else "ဆာဗာ ချို့ယွင်းချက်: $code"

            if (isSuccess && (resJson?.optBoolean("success", true) != false)) {
                Result.success(message)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

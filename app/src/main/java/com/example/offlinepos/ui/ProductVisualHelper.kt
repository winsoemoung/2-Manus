package com.example.offlinepos.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinepos.data.local.ProductEntity
import java.io.File

object ProductVisualHelper {

    fun getCategoryEmoji(category: String): String {
        val lower = category.lowercase()
        return when {
            lower.contains("ဘိလပ်မြေ") || lower.contains("cement") -> "🧱"
            lower.contains("သွပ်") || lower.contains("roof") -> "🏠"
            lower.contains("သံ") || lower.contains("steel") || lower.contains("rebar") -> "🏗️"
            lower.contains("ဆေး") || lower.contains("paint") -> "🎨"
            lower.contains("ပိုက်") || lower.contains("pvc") || lower.contains("pipe") -> "🚰"
            lower.contains("မှို") || lower.contains("nail") || lower.contains("ကိရိယာ") || lower.contains("tool") -> "🔨"
            else -> "📦"
        }
    }

    fun getCategoryGradient(category: String): Brush {
        val lower = category.lowercase()
        return when {
            lower.contains("ဘိလပ်မြေ") || lower.contains("cement") -> Brush.linearGradient(
                listOf(Color(0xFF64748B), Color(0xFF475569))
            )
            lower.contains("သွပ်") || lower.contains("roof") -> Brush.linearGradient(
                listOf(Color(0xFF0284C7), Color(0xFF0369A1))
            )
            lower.contains("သံ") || lower.contains("steel") -> Brush.linearGradient(
                listOf(Color(0xFF334155), Color(0xFF1E293B))
            )
            lower.contains("ဆေး") || lower.contains("paint") -> Brush.linearGradient(
                listOf(Color(0xFFD97706), Color(0xFFEA580C))
            )
            lower.contains("ပိုက်") || lower.contains("pvc") -> Brush.linearGradient(
                listOf(Color(0xFF0D9488), Color(0xFF0F766E))
            )
            else -> Brush.linearGradient(
                listOf(Color(0xFF4B5563), Color(0xFF374151))
            )
        }
    }

    @Composable
    fun ProductVisual(
        product: ProductEntity,
        modifier: Modifier = Modifier,
    ) {
        val customBitmap = remember(product.imageUri) {
            if (!product.imageUri.isNullOrBlank()) {
                try {
                    val file = File(product.imageUri)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(RoundedCornerShape(8.dp)),
            )
        } else {
            // Hardware material themed badge (No food images!)
            val emoji = getCategoryEmoji(product.category)
            val brush = getCategoryGradient(product.category)

            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 32.sp,
                )
            }
        }
    }
}

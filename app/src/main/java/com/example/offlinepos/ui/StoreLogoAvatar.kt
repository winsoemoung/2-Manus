package com.example.offlinepos.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.offlinepos.R
import java.io.File

@Composable
fun StoreLogoAvatar(
    customLogoPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Store Logo",
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
) {
    val bitmap = remember(customLogoPath) {
        if (!customLogoPath.isNullOrBlank()) {
            val cleanPath = customLogoPath.substringBefore("?")
            val file = File(cleanPath)
            if (file.exists() && file.canRead()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    val finalModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = finalModifier,
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_lady_owner),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = finalModifier,
        )
    }
}

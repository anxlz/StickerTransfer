package com.stickertransfer.app.utils

import android.content.Context
import android.content.Intent
import com.stickertransfer.app.data.model.StickerPack

object WhatsAppUtils {

    fun isWhatsAppInstalled(context: Context): Boolean =
        isPackageInstalled(context, "com.whatsapp")

    fun isWhatsAppBusinessInstalled(context: Context): Boolean =
        isPackageInstalled(context, "com.whatsapp.w4b")

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Build the WhatsApp sticker intent.
     */
    fun buildStickerIntent(
        context: Context,
        pack: StickerPack,
        forBusiness: Boolean = false
    ): Intent {
        // Authority must match exactly with AndroidManifest.xml
        val authority = "${context.packageName}.StickerContentProvider"
        return Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            setPackage(if (forBusiness) "com.whatsapp.w4b" else "com.whatsapp")
            putExtra("sticker_pack_id", pack.identifier)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", pack.name)
        }
    }

    fun addStickerPackToWhatsApp(
        context: Context,
        pack: StickerPack,
        forBusiness: Boolean = false
    ): Boolean {
        return try {
            val intent = buildStickerIntent(context, pack, forBusiness)
            // Note: In Compose, it's better to use ActivityResultLauncher.
            // This is kept for backward compatibility or non-UI usage.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

package com.stickertransfer.app.utils

import android.content.Context
import android.content.Intent
import com.stickertransfer.app.BuildConfig
import com.stickertransfer.app.data.model.StickerPack

object WhatsAppUtils {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    private const val ADD_PACK_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    fun isWhatsAppInstalled(context: Context): Boolean =
        isPackageInstalled(context, WHATSAPP_PACKAGE)

    fun isWhatsAppBusinessInstalled(context: Context): Boolean =
        isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch the WhatsApp "Add Sticker Pack" intent.
     * WhatsApp will call back to our StickerContentProvider to retrieve pack data.
     */
    fun addStickerPackToWhatsApp(
        context: Context,
        pack: StickerPack,
        useWhatsAppBusiness: Boolean = false
    ): Boolean {
        val targetPackage = if (useWhatsAppBusiness) WHATSAPP_BUSINESS_PACKAGE else WHATSAPP_PACKAGE
        if (!isPackageInstalled(context, targetPackage)) return false

        val intent = Intent(ADD_PACK_ACTION).apply {
            setPackage(targetPackage)
            putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.PROVIDER_AUTHORITY)
            putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

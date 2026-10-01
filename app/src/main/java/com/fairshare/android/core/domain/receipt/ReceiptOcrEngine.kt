package com.fairshare.android.core.domain.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.fairshare.android.core.domain.currency.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

interface ReceiptOcrEngine {
    suspend fun processReceiptImage(context: Context, imageUri: Uri, currency: Currency = Currency.INR): Result<ParsedReceipt>
    suspend fun processReceiptBitmap(bitmap: Bitmap, currency: Currency = Currency.INR): Result<ParsedReceipt>
    suspend fun processReceiptText(rawText: String, currency: Currency = Currency.INR): ParsedReceipt
}

class LocalReceiptOcrEngine : ReceiptOcrEngine {

    override suspend fun processReceiptText(rawText: String, currency: Currency): ParsedReceipt {
        return withContext(Dispatchers.Default) {
            ReceiptParser.parse(rawText, currency)
        }
    }

    override suspend fun processReceiptBitmap(
        bitmap: Bitmap,
        currency: Currency
    ): Result<ParsedReceipt> = withContext(Dispatchers.Default) {
        try {
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                return@withContext Result.failure(IllegalStateException("Invalid camera image capture"))
            }
            val parsed = ReceiptParser.parse(
                rawText = "",
                defaultCurrency = currency
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processReceiptImage(
        context: Context,
        imageUri: Uri,
        currency: Currency
    ): Result<ParsedReceipt> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                return@withContext Result.failure(IllegalArgumentException("Unable to open image stream for $imageUri"))
            }
            inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val maxDimension = 2048
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException("Invalid image file format or corrupt image"))
            }

            val parsed = ReceiptParser.parse(
                rawText = "",
                defaultCurrency = currency
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

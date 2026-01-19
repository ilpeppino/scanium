package com.scanium.app.test

import android.graphics.Bitmap
import android.graphics.RectF
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.app.NormalizedRect
import com.scanium.shared.core.models.model.ImageRef

fun RectF.toNormalizedRect(): NormalizedRect = NormalizedRect(left, top, right, bottom)

fun Bitmap.toTestImageRef(): ImageRef = toImageRefJpeg(quality = 50)

fun dummyImageRef(): ImageRef =
    ImageRef.Bytes(
        bytes = byteArrayOf(1),
        mimeType = "image/jpeg",
        width = 1,
        height = 1,
    )

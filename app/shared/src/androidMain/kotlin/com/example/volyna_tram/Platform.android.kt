package com.example.volyna_tram

import android.os.Build
import com.example.volyna_tram.domain.model.TramElement

class AndroidPlatform : TramElement.Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): TramElement.Platform = AndroidPlatform()
package com.example.volyna_tram

import android.os.Build
import com.example.volyna_tram.domain.model.TramElement


actual fun getPlatform(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
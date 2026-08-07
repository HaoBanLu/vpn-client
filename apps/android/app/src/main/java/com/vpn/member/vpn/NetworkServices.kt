package com.vpn.member.vpn

import android.app.Application
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.core.content.getSystemService

object NetworkServices {
    lateinit var application: Application
        private set

    fun init(app: Application) {
        application = app
    }

    val connectivity: ConnectivityManager
        get() = application.getSystemService()!!

    val packageManager: PackageManager
        get() = application.packageManager
}

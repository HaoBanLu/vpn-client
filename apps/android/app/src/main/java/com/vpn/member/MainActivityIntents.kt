package com.vpn.member

import android.content.Context
import android.content.Intent

/** MainActivity 启动 Intent 统一入口，避免通知/服务重复创建 Activity 栈。 */
object MainActivityIntents {
    fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
}

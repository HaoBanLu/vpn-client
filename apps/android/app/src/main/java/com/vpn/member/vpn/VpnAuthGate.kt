package com.vpn.member.vpn

import android.content.Context
import com.vpn.member.data.local.TokenStore

/** VPN 恢复/自启前校验：须已登录且持有有效 JWT。 */
object VpnAuthGate {
    fun isLoggedIn(context: Context): Boolean {
        val token = TokenStore(context.applicationContext).getJwt()
        return !token.isNullOrBlank()
    }
}

package com.vpn.member.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiErrorsTest {
    @Test
    fun isHardApiFailure_detectsDnsAndConnectionRefused() {
        assertTrue(ApiErrors.isHardApiFailure(UnknownHostException("vpn.example.com")))
        assertTrue(ApiErrors.isHardApiFailure(ConnectException("failed to connect")))
        assertTrue(ApiErrors.isHardApiFailure(SSLException("certificate")))
        assertTrue(ApiErrors.isHardApiFailure(IOException("Unable to resolve host")))
        assertTrue(ApiErrors.isHardApiFailure(IOException("Connection refused")))
    }

    @Test
    fun shouldSuppressPageError_onlySessionInvalidated() {
        assertTrue(ApiErrors.shouldSuppressPageError(SessionInvalidatedException()))
        assertFalse(ApiErrors.shouldSuppressPageError(UnknownHostException("vpn.example.com")))
        assertFalse(ApiErrors.shouldSuppressPageError(IOException("connection reset")))
    }
}

package com.agent.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEndpointTest {

    @Test
    fun keepsLanHttpOnDefaultPort() {
        val endpoint = parseAgentEndpoint("10.109.32.248", 8787)
        assertEquals("10.109.32.248", endpoint.host)
        assertEquals(8787, endpoint.port)
        assertFalse(endpoint.secure)
        assertEquals("http://10.109.32.248:8787", endpoint.httpBase)
        assertEquals("ws://10.109.32.248:8787", endpoint.wsBase)
    }

    @Test
    fun usesHttpsForCloudflareTunnelHostname() {
        val endpoint = parseAgentEndpoint("random-name.trycloudflare.com", 8787)
        assertEquals("random-name.trycloudflare.com", endpoint.host)
        assertEquals(443, endpoint.port)
        assertTrue(endpoint.secure)
        assertEquals("https://random-name.trycloudflare.com", endpoint.httpBase)
        assertEquals("wss://random-name.trycloudflare.com", endpoint.wsBase)
    }

    @Test
    fun parsesFullHttpsUrl() {
        val endpoint = parseAgentEndpoint("https://abc.trycloudflare.com/pair", 8787)
        assertEquals("abc.trycloudflare.com", endpoint.host)
        assertEquals(443, endpoint.port)
        assertTrue(endpoint.secure)
    }

    @Test
    fun honorsExplicitHttpScheme() {
        val endpoint = parseAgentEndpoint("http://127.0.0.1:8787", 80)
        assertEquals("127.0.0.1", endpoint.host)
        assertEquals(8787, endpoint.port)
        assertFalse(endpoint.secure)
    }
}

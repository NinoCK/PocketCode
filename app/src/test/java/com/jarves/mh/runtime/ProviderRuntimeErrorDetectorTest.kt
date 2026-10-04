package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderRuntimeErrorDetectorTest {
    @Test
    fun userNotFoundIsFatal() {
        assertEquals(
            "User not found. Check the API key and provider account.",
            ProviderRuntimeErrorDetector.detect("Failed to authenticate. API Error: 401 User not found."),
        )
    }

    @Test
    fun modelOutputMentioningLimitsIsNotAnError() {
        val reply = """{"type":"assistant","message":{"content":[{"type":"text","text":"I added rate limiting so expired tokens hit the quota check."}],"stop_reason":"end_turn"}}"""
        assertNull(ProviderRuntimeErrorDetector.detect(reply))
        assertNull(ProviderRuntimeErrorDetector.detect(reply, subscription = true))
        val question = """{"type":"control_request","request_id":"r","request":{"subtype":"can_use_tool","tool_name":"AskUserQuestion","input":{"questions":[{"question":"Add rate limiting?"}]}}}"""
        assertNull(ProviderRuntimeErrorDetector.detect(question, subscription = true))
        val result = """{"type":"result","subtype":"success","is_error":false,"result":"Rate limit middleware is in place."}"""
        assertNull(ProviderRuntimeErrorDetector.detect(result))
        val failedCall = """{"type":"assistant","error":"rate_limit","message":{"content":[{"type":"text","text":"API Error: Rate limit reached"}]}}"""
        assertEquals("The provider rejected the saved API key.", ProviderRuntimeErrorDetector.detect(failedCall))
    }

    @Test
    fun authenticationRetryIsFatalImmediately() {
        val event = """{"type":"system","subtype":"api_retry","attempt":1,"error_status":401,"error":"authentication_failed"}"""
        assertEquals(
            "The provider rejected the saved API key.",
            ProviderRuntimeErrorDetector.detect(event),
        )
    }

    @Test
    fun ordinaryRuntimeOutputIsNotFatal() {
        assertNull(ProviderRuntimeErrorDetector.detect("Claude Code connected"))
    }
}

package com.huawei.ascend.client.kotlin

import com.huawei.ascend.client.SendSpec

/**
 * [SendSpec] with Kotlin named/default arguments, mirroring the record's
 * canonical constructor: a null/blank [messageId] is replaced by a generated
 * one and [metadata] is defensively copied (reserved routing keys still win).
 */
fun sendSpec(
    agentId: String,
    sessionId: String,
    userId: String,
    text: String,
    messageId: String? = null,
    metadata: Map<String, Any> = emptyMap(),
): SendSpec = SendSpec(agentId, sessionId, userId, text, messageId, metadata)

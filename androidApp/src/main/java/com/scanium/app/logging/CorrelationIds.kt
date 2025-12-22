package com.scanium.app.logging

import java.util.UUID

object CorrelationIds {
    @Volatile
    private var classificationSessionId: String = newSessionId("cls")

    fun currentClassificationSessionId(): String = classificationSessionId

    fun startNewClassificationSession(): String {
        classificationSessionId = newSessionId("cls")
        return classificationSessionId
    }

    fun newAssistRequestId(): String = newSessionId("assist")

    fun newDraftRequestId(): String = newSessionId("draft")

    private fun newSessionId(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }
}

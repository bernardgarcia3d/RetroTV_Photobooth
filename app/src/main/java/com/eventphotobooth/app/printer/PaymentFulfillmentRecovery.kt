package com.eventphotobooth.app.printer

/**
 * Explicit physical-print lifecycle for paid sessions. USB transports can report
 * a write failure after bytes have reached a printer; that outcome is not safe
 * to retry automatically because it can produce a duplicate print.
 */
enum class PhysicalPrintOutcome {
    QUEUED,
    SUBMITTING,
    SUBMITTED,
    COMPLETED,
    FAILED_BEFORE_SUBMISSION,
    OUTCOME_UNCERTAIN,
}

data class PrintRecoveryState(
    val requestedCopies: Int,
    val completedCopies: Int = 0,
    val outcome: PhysicalPrintOutcome = PhysicalPrintOutcome.QUEUED,
    val attentionMessage: String? = null,
) {
    val remainingCopies: Int
        get() = (requestedCopies - completedCopies).coerceAtLeast(0)

    val mayRetryAutomatically: Boolean
        get() = outcome == PhysicalPrintOutcome.QUEUED || outcome == PhysicalPrintOutcome.FAILED_BEFORE_SUBMISSION

    val requiresOperatorConfirmation: Boolean
        get() = outcome == PhysicalPrintOutcome.OUTCOME_UNCERTAIN

    init {
        require(requestedCopies in 1..10)
        require(completedCopies in 0..requestedCopies)
    }
}

object PrintRecoveryPolicy {
    fun beforeUsbSubmission(state: PrintRecoveryState): PrintRecoveryState =
        state.copy(outcome = PhysicalPrintOutcome.SUBMITTING, attentionMessage = null)

    fun afterUsbSubmission(state: PrintRecoveryState): PrintRecoveryState =
        state.copy(outcome = PhysicalPrintOutcome.SUBMITTED, attentionMessage = null)

    fun copyCompleted(state: PrintRecoveryState): PrintRecoveryState {
        val completed = (state.completedCopies + 1).coerceAtMost(state.requestedCopies)
        return state.copy(
            completedCopies = completed,
            outcome = if (completed == state.requestedCopies) PhysicalPrintOutcome.COMPLETED else PhysicalPrintOutcome.QUEUED,
            attentionMessage = null,
        )
    }

    fun failedBeforeSubmission(state: PrintRecoveryState, detail: String): PrintRecoveryState =
        state.copy(outcome = PhysicalPrintOutcome.FAILED_BEFORE_SUBMISSION, attentionMessage = detail.take(220))

    fun uncertainAfterSubmission(state: PrintRecoveryState, detail: String): PrintRecoveryState =
        state.copy(
            outcome = PhysicalPrintOutcome.OUTCOME_UNCERTAIN,
            attentionMessage = "Print outcome is unknown. Confirm the physical output before retrying. ${detail.take(160)}",
        )
}


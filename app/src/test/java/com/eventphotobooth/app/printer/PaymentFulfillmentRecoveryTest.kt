package com.eventphotobooth.app.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFulfillmentRecoveryTest {
    @Test
    fun `USB interruption requires an operator before retrying`() {
        val submitting = PrintRecoveryPolicy.beforeUsbSubmission(PrintRecoveryState(requestedCopies = 3))
        val uncertain = PrintRecoveryPolicy.uncertainAfterSubmission(submitting, "USB connection dropped")

        assertEquals(PhysicalPrintOutcome.OUTCOME_UNCERTAIN, uncertain.outcome)
        assertTrue(uncertain.requiresOperatorConfirmation)
        assertFalse(uncertain.mayRetryAutomatically)
        assertEquals(3, uncertain.remainingCopies)
    }

    @Test
    fun `successful submissions retain multi copy progress`() {
        var state = PrintRecoveryState(requestedCopies = 2)
        state = PrintRecoveryPolicy.beforeUsbSubmission(state)
        state = PrintRecoveryPolicy.afterUsbSubmission(state)
        state = PrintRecoveryPolicy.copyCompleted(state)

        assertEquals(1, state.completedCopies)
        assertEquals(1, state.remainingCopies)
        assertEquals(PhysicalPrintOutcome.QUEUED, state.outcome)

        state = PrintRecoveryPolicy.beforeUsbSubmission(state)
        state = PrintRecoveryPolicy.afterUsbSubmission(state)
        state = PrintRecoveryPolicy.copyCompleted(state)

        assertEquals(2, state.completedCopies)
        assertEquals(PhysicalPrintOutcome.COMPLETED, state.outcome)
    }
}


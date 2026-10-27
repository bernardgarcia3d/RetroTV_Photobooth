package com.eventphotobooth.app.printer

import com.eventphotobooth.app.data.LocalSessionStore
import com.eventphotobooth.app.data.PrintJob
import com.eventphotobooth.app.data.PrintStatus

/**
 * The queue is durable before any USB-printer adapter is introduced. A printer
 * adapter must acknowledge a job before it is marked printed, preventing a retry
 * from silently creating a second receipt.
 */
class LocalPrintQueue(
    private val store: LocalSessionStore,
) {
    fun enqueue(sessionId: String, stripPath: String, quantity: Int = 1): PrintJob {
        val job = PrintJob(sessionId = sessionId, stripPath = stripPath, quantity = quantity.coerceIn(1, 10))
        store.savePrintJob(job)
        store.updatePrintStatus(sessionId, PrintStatus.QUEUED)
        return job
    }

    fun markPrinting(job: PrintJob): PrintJob {
        require(!job.operatorAttentionRequired) { "This print has an uncertain physical outcome and needs operator confirmation before retrying." }
        val updated = job.copy(
            status = PrintStatus.PRINTING,
            attempts = job.attempts + 1,
            physicalOutcome = PhysicalPrintOutcome.SUBMITTING,
            lastError = null,
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.PRINTING)
        return updated
    }

    fun markCopyCompleted(job: PrintJob): PrintJob {
        val updated = job.copy(
            completedCopies = (job.completedCopies + 1).coerceAtMost(job.quantity),
            physicalOutcome = PhysicalPrintOutcome.SUBMITTED,
            status = if (job.completedCopies + 1 >= job.quantity) PrintStatus.SUBMITTED else PrintStatus.PRINTING,
            lastError = null,
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, updated.status)
        return updated
    }

    /** Marks the next copy as actively writing to USB before any printer bytes are sent. */
    fun markSubmittingNextCopy(job: PrintJob): PrintJob {
        require(!job.operatorAttentionRequired) { "This print needs operator review before retrying." }
        val updated = job.copy(
            status = PrintStatus.PRINTING,
            physicalOutcome = PhysicalPrintOutcome.SUBMITTING,
            lastError = null,
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.PRINTING)
        return updated
    }

    /**
     * The USB transport accepted all bytes. This is intentionally not named
     * "printed" because commodity USB receipt printers do not prove paper
     * delivery back to Android.
     */
    fun markSubmitted(job: PrintJob): PrintJob {
        val updated = job.copy(
            status = PrintStatus.SUBMITTED,
            completedCopies = job.quantity,
            physicalOutcome = PhysicalPrintOutcome.SUBMITTED,
            operatorAttentionRequired = false,
            lastError = null,
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.SUBMITTED)
        return updated
    }

    fun markFailed(job: PrintJob, error: String): PrintJob {
        val updated = job.copy(
            status = PrintStatus.FAILED,
            physicalOutcome = PhysicalPrintOutcome.FAILED_BEFORE_SUBMISSION,
            operatorAttentionRequired = false,
            lastError = error.take(220),
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.FAILED)
        return updated
    }

    /** Call this if USB fails or disconnects after a submission attempt began. */
    fun markOutcomeUncertain(job: PrintJob, error: String): PrintJob {
        val updated = job.copy(
            status = PrintStatus.FAILED,
            physicalOutcome = PhysicalPrintOutcome.OUTCOME_UNCERTAIN,
            operatorAttentionRequired = true,
            lastError = "Output may have started. Confirm paper before retrying. ${error.take(150)}",
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.FAILED)
        return updated
    }

    /** Records an operator's deliberate review before allowing the same job to retry. */
    fun clearUncertainOutcomeForOperatorRetry(job: PrintJob): PrintJob {
        require(job.operatorAttentionRequired) { "No operator review is required for this print." }
        val updated = job.copy(
            status = PrintStatus.QUEUED,
            physicalOutcome = PhysicalPrintOutcome.QUEUED,
            operatorAttentionRequired = false,
            lastError = null,
        )
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.QUEUED)
        return updated
    }
}


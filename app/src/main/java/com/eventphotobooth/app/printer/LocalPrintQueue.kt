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
        val updated = job.copy(status = PrintStatus.PRINTING, attempts = job.attempts + 1)
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.PRINTING)
        return updated
    }

    fun markPrinted(job: PrintJob): PrintJob {
        val updated = job.copy(status = PrintStatus.PRINTED, lastError = null)
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.PRINTED)
        return updated
    }

    fun markFailed(job: PrintJob, error: String): PrintJob {
        val updated = job.copy(status = PrintStatus.FAILED, lastError = error)
        store.savePrintJob(updated)
        store.updatePrintStatus(job.sessionId, PrintStatus.FAILED)
        return updated
    }
}

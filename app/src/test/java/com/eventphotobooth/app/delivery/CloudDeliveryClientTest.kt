package com.eventphotobooth.app.delivery

import com.eventphotobooth.app.data.BoothConfiguration
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDeliveryClientTest {
    @Test
    fun `retries a temporary Worker failure before falling back to local copies`() {
        val requests = AtomicInteger(0)
        val server = startServer { exchange ->
            exchange.requestBody.readBytes()
            requests.incrementAndGet()
            respond(exchange, 503, "{\"error\":\"temporarily unavailable\"}")
        }
        val files = temporaryAssets()

        try {
            val result = CloudDeliveryClient().upload(configurationFor(server), SESSION_ID, files.first, files.second)

            assertEquals(3, requests.get())
            assertEquals(3, result.attempts)
            assertNull(result.links)
            assertTrue(result.failureMessage!!.contains("unavailable after 3 attempts"))
        } finally {
            server.stop(0)
            files.toList().forEach(File::delete)
        }
    }

    @Test
    fun `does not retry a bad Worker route and returns a safe kiosk message`() {
        val requests = AtomicInteger(0)
        val server = startServer { exchange ->
            exchange.requestBody.readBytes()
            requests.incrementAndGet()
            respond(exchange, 404, "{\"error\":\"Not found\"}")
        }
        val files = temporaryAssets()

        try {
            val result = CloudDeliveryClient().upload(configurationFor(server), SESSION_ID, files.first, files.second)

            assertEquals(1, requests.get())
            assertEquals(1, result.attempts)
            assertNull(result.links)
            assertNotNull(result.failureMessage)
            assertTrue(result.failureMessage!!.contains("setup needs attention"))
        } finally {
            server.stop(0)
            files.toList().forEach(File::delete)
        }
    }

    private fun startServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/sessions/$SESSION_ID/assets") { exchange -> handler(exchange) }
            start()
        }

    private fun configurationFor(server: HttpServer) = BoothConfiguration(
        deliveryWorkerUrl = "http://127.0.0.1:${server.address.port}",
        deliveryBoothId = "booth-001",
        deliveryDeviceKey = "test-key",
    )

    private fun temporaryAssets(): Pair<File, File> {
        val photo = File.createTempFile("photobooth-test-", ".png").apply { writeText("photo") }
        val gif = File.createTempFile("photobooth-test-", ".gif").apply { writeText("gif") }
        return photo to gif
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val SESSION_ID = "00000000-0000-4000-8000-000000000001"
    }
}

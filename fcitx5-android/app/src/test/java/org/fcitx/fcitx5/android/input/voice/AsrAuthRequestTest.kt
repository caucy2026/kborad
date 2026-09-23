package org.fcitx.fcitx5.android.input.voice

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class AsrAuthRequestTest {
    @Test fun nextAuthorizationDoesNotWaitOnPreviousIdleSocket() {
        withServer { server ->
            val releaseFirst = CountDownLatch(1)
            val secondConnection = CountDownLatch(1)
            val worker = thread(isDaemon = true) {
                server.accept().use { first ->
                    readRequest(first)
                    val body = "authorized"
                    first.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: keep-alive\r\n\r\n$body".toByteArray()
                    )
                    first.getOutputStream().flush()
                    server.accept().use { second ->
                        secondConnection.countDown()
                        readRequest(second)
                        respond(second, 200, body)
                    }
                    releaseFirst.await(4, TimeUnit.SECONDS)
                }
            }
            try {
                val sharedClient = newAsrAuthHttpClient(1200)
                assertEquals("authorized", runRequest(server, sharedClient).getOrThrow().body)
                val done = CountDownLatch(1)
                val result = AtomicReference<Result<AsrAuthResponse>>()
                AsrAuthRequest(sharedClient, { request(server) }, { result.set(it); done.countDown() }).start()
                assertTrue("new session reused a silent old socket", secondConnection.await(700, TimeUnit.MILLISECONDS))
                assertTrue(done.await(2, TimeUnit.SECONDS))
                assertEquals("authorized", result.get().getOrThrow().body)
            } finally {
                releaseFirst.countDown()
                worker.join(1000)
            }
        }
    }

    @Test fun permissionFailureTerminatesWithoutRetry() {
        val attempts = AtomicInteger()
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val client = client().newBuilder().addInterceptor {
            attempts.incrementAndGet()
            throw SecurityException("test denied network permission")
        }.build()
        AsrAuthRequest(client, { Request.Builder().url("http://127.0.0.1/auth").build() }, {
            failure.set(it.exceptionOrNull())
            done.countDown()
        }).start()
        assertTrue("permission failure must end the session", done.await(2, TimeUnit.SECONDS))
        assertTrue(failure.get() is SecurityException)
        assertEquals(1, attempts.get())
    }

    @Test fun cleartextPolicyFailureIsNotRetried() {
        val attempts = AtomicInteger()
        val done = CountDownLatch(1)
        val client = client().newBuilder().addInterceptor {
            attempts.incrementAndGet()
            throw java.net.UnknownServiceException("test cleartext policy")
        }.build()
        val failure = AtomicReference<Throwable>()
        AsrAuthRequest(client, { Request.Builder().url("http://127.0.0.1/auth").build() }, {
            failure.set(it.exceptionOrNull())
            done.countDown()
        }).start()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertTrue(failure.get() is java.net.UnknownServiceException)
        assertEquals(1, attempts.get())
    }

    // Real loopback HTTP: a stalled first response must not consume the entire press.
    @Test fun stalledAuthenticationRetriesAndReturnsSecondResponse() {
        withServer { server ->
            val accepted = AtomicInteger()
            val releaseFirst = CountDownLatch(1)
            val worker = thread(isDaemon = true) {
                server.accept().use { first ->
                    accepted.incrementAndGet()
                    readRequest(first)
                    thread(isDaemon = true) {
                        server.accept().use { second ->
                            accepted.incrementAndGet()
                            readRequest(second)
                            respond(second, 200, "authorized")
                        }
                    }
                    releaseFirst.await(5, TimeUnit.SECONDS)
                }
            }
            try {
                val result = runRequest(server)
                assertEquals("authorized", result.getOrThrow().body)
                assertEquals(2, accepted.get())
            } finally {
                releaseFirst.countDown()
                worker.join(1000)
            }
        }
    }

    @Test fun authorizationRejectionIsReturnedWithoutRetry() {
        withServer { server ->
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    readRequest(socket)
                    respond(socket, 403, "rejected")
                }
            }
            val result = runRequest(server).getOrThrow()
            assertEquals(403, result.code)
            assertEquals("rejected", result.body)
            server.soTimeout = 200
            assertThrows(java.net.SocketTimeoutException::class.java) { server.accept() }
        }
    }

    @Test fun businessRejectionInSuccessfulHttpResponseIsNotRetried() {
        withServer { server ->
            val rejection = "{\"code\":\"00000\",\"data\":{\"status\":1,\"msg\":\"denied\"}}"
            thread(isDaemon = true) {
                server.accept().use { socket -> readRequest(socket); respond(socket, 200, rejection) }
            }
            assertEquals(rejection, runRequest(server).getOrThrow().body)
            server.soTimeout = 200
            assertThrows(java.net.SocketTimeoutException::class.java) { server.accept() }
        }
    }

    @Test fun transientServerFailureRetriesOnceButNeverLoops() {
        withServer { server ->
            val accepted = AtomicInteger()
            thread(isDaemon = true) {
                repeat(2) {
                    server.accept().use { socket ->
                        accepted.incrementAndGet()
                        readRequest(socket)
                        respond(socket, 503, "unavailable")
                    }
                }
            }
            assertEquals(503, runRequest(server).getOrThrow().code)
            assertEquals(2, accepted.get())
            server.soTimeout = 200
            assertThrows(java.net.SocketTimeoutException::class.java) { server.accept() }
        }
    }

    @Test fun cancelDuringAuthenticationSuppressesRetryAndCallback() {
        withServer { server ->
            val received = CountDownLatch(1)
            val release = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    readRequest(socket)
                    received.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            val request = AsrAuthRequest(client(), { request(server) }, { completed.countDown() })
            try {
                request.start()
                assertTrue(received.await(2, TimeUnit.SECONDS))
                request.cancel()
                assertFalse(completed.await(500, TimeUnit.MILLISECONDS))
                server.soTimeout = 200
                assertThrows(java.net.SocketTimeoutException::class.java) { server.accept() }
            } finally {
                release.countDown()
                worker.join(1000)
            }
        }
    }

    @Test fun cancelledOldRequestDoesNotBlockNewRequest() {
        withServer { server ->
            val received = CountDownLatch(1)
            val release = CountDownLatch(1)
            val oldCallbacks = AtomicInteger()
            val worker = thread(isDaemon = true) {
                server.accept().use { first ->
                    readRequest(first)
                    received.countDown()
                    server.accept().use { second ->
                        readRequest(second)
                        respond(second, 200, "new session")
                    }
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            try {
                val old = AsrAuthRequest(client(), { request(server) }, { oldCallbacks.incrementAndGet() })
                old.start()
                assertTrue(received.await(2, TimeUnit.SECONDS))
                old.cancel()
                assertEquals("new session", runRequest(server).getOrThrow().body)
                assertEquals(0, oldCallbacks.get())
            } finally {
                release.countDown()
                worker.join(1000)
            }
        }
    }

    private fun client() = OkHttpClient.Builder()
        .callTimeout(300, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun request(server: ServerSocket) = Request.Builder()
        .url("http://127.0.0.1:${server.localPort}/auth").build()

    private fun runRequest(server: ServerSocket, calls: okhttp3.Call.Factory = client()): Result<AsrAuthResponse> {
        val done = CountDownLatch(1)
        val result = AtomicReference<Result<AsrAuthResponse>>()
        AsrAuthRequest(calls, { request(server) }, { result.set(it); done.countDown() }).start()
        assertTrue("authentication did not terminate", done.await(4, TimeUnit.SECONDS))
        return result.get()
    }

    private fun withServer(block: (ServerSocket) -> Unit) = ServerSocket(0, 4,
        java.net.InetAddress.getByName("127.0.0.1")).use(block)

    private fun readRequest(socket: java.net.Socket) {
        socket.soTimeout = 2000
        val reader = socket.getInputStream().bufferedReader()
        while (!reader.readLine().isNullOrEmpty()) Unit
    }

    private fun respond(socket: java.net.Socket, code: Int, body: String) {
        socket.getOutputStream().write(
            "HTTP/1.1 $code Result\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
        socket.getOutputStream().flush()
    }
}

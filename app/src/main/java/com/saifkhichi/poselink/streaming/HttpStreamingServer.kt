package com.saifkhichi.poselink.streaming

import fi.iki.elonen.NanoHTTPD
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class HttpStreamingServer(
    port: Int,
    private val frames: JpegFrameProvider,
    private val sensors: SensorJsonProvider? = null,
    private val maxFps: Int = 20
) : NanoHTTPD(port) {

    private val running = AtomicBoolean(false)

    fun startServer() {
        if (running.compareAndSet(false, true)) start(SOCKET_READ_TIMEOUT, false)
    }

    fun stopServer() {
        if (running.compareAndSet(true, false)) stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (!running.get()) {
            return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                MIME_PLAINTEXT,
                "Server stopping"
            )
        }

        return when (session.uri) {
            "/health" -> newFixedLengthResponse("OK")
            "/sensors.json" -> {
                val body = sensors?.snapshotJson() ?: "{}"
                newFixedLengthResponse(Response.Status.OK, "application/json", body)
            }

            "/shot.jpg" -> {
                val jpg = frames.latestJpeg()
                if (jpg == null) newFixedLengthResponse(
                    Response.Status.NO_CONTENT,
                    MIME_PLAINTEXT,
                    ""
                )
                else newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    ByteArrayInputStream(jpg),
                    jpg.size.toLong()
                )
            }

            "/video" -> mjpegResponse()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun mjpegResponse(): Response {
        val boundary = "frame"
        val mime = "multipart/x-mixed-replace; boundary=$boundary"

        // Pipe used to stream bytes to client in a background writer
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut, 64 * 1024)

        val writer = Thread({
            val intervalMs = (1000.0 / maxFps.coerceAtLeast(1)).toLong()
            val headerTemplate =
                "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n"

            var lastWrite = 0L
            try {
                val out = BufferedOutputStream(pipeOut)
                while (running.get()) {
                    val jpg = frames.latestJpeg()
                    if (jpg != null) {
                        // throttle
                        val now = System.currentTimeMillis()
                        val until = lastWrite + intervalMs
                        if (now < until) {
                            try {
                                Thread.sleep(until - now)
                            } catch (_: InterruptedException) {
                            }
                        }
                        val hdr = headerTemplate.format(jpg.size).toByteArray()
                        out.write(hdr)
                        out.write(jpg)
                        out.write("\r\n".toByteArray())
                        out.flush()
                        lastWrite = System.currentTimeMillis()
                    } else {
                        // nothing yet; avoid busy loop
                        try {
                            Thread.sleep(10)
                        } catch (_: InterruptedException) {
                        }
                    }
                }
            } catch (_: IOException) {
                // client closed connection or pipe broken; exit quietly
            } finally {
                try {
                    pipeOut.close()
                } catch (_: IOException) {
                }
            }
        }, "mjpeg-writer-${System.nanoTime()}")

        writer.isDaemon = true
        writer.start()

        return newChunkedResponse(Response.Status.OK, mime, pipeIn)
            .apply {
                // Allow long-lived connection
                addHeader("Connection", "close")
                // Most clients ignore cache headers for MJPEG, but set anyway
                addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                addHeader("Pragma", "no-cache")
            }
    }
}
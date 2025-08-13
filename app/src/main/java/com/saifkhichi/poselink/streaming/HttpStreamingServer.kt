package com.saifkhichi.poselink.streaming

import android.os.SystemClock
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
) : NanoHTTPD("0.0.0.0", port) {

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
            ).withCors()
        }

        // Simple CORS preflight
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
                .withCors()
        }

        return when (session.uri) {
            "/health" -> newFixedLengthResponse("OK").withCors()

            // Monotonic clock for host<->phone offset estimation
            "/clock" -> {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val body = """{"t_ns": $nowNs}"""
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    body
                ).withCors()
            }

            // --- add inside `when (session.uri)` in HttpStreamingServer.serve(...) ---

            "/calib.json" -> {
                val body = sensors?.calibrationJson()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    body
                ).withCors()
            }

            "/sensors.json" -> {
                val body = sensors?.snapshotJsonCalibrated()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    body
                ).withCors()
            }

            "/sensors-raw.json" -> {
                val body = sensors?.snapshotJson() ?: "{}"
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    body
                ).withCors()
            }

            "/shot.jpg" -> {
                val jpg = frames.latestJpeg()
                if (jpg == null) {
                    newFixedLengthResponse(
                        Response.Status.NO_CONTENT,
                        MIME_PLAINTEXT,
                        ""
                    ).withCors()
                } else {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "image/jpeg",
                        ByteArrayInputStream(jpg),
                        jpg.size.toLong()
                    ).apply {
                        val nowNs = System.nanoTime()
                        addHeader("X-PoseLink-TimestampNs", nowNs.toString())
                        addHeader("X-PoseLink-ClockBase", "CLOCK_MONOTONIC")
                    }.withCors()
                }
            }

            "/video" -> mjpegResponse()

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            ).withCors()
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
            var lastWrite = 0L
            try {
                val out = BufferedOutputStream(pipeOut)
                while (running.get()) {
                    val jpg = frames.latestJpeg()
                    if (jpg != null) {
                        val nowNs = System.nanoTime()
                        val nowMs = System.currentTimeMillis()
                        val until = lastWrite + intervalMs
                        if (nowMs < until) {
                            try {
                                Thread.sleep(until - nowMs)
                            } catch (_: InterruptedException) {
                            }
                        }
                        val hdr = buildString {
                            append("--$boundary\r\n")
                            append("Content-Type: image/jpeg\r\n")
                            append("X-PoseLink-TimestampNs: $nowNs\r\n")
                            append("X-PoseLink-ClockBase: CLOCK_MONOTONIC\r\n")
                            append("Content-Length: ${jpg.size}\r\n\r\n")
                        }.toByteArray()
                        out.write(hdr)
                        out.write(jpg)
                        out.write("\r\n".toByteArray())
                        out.flush()
                        lastWrite = System.currentTimeMillis()
                    } else {
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
        }, "mjpeg-writer-${System.nanoTime()}").apply { isDaemon = true }

        writer.start()

        return newChunkedResponse(Response.Status.OK, mime, pipeIn).apply {
            // Long-lived connection
            addHeader("Connection", "keep-alive")
            // Cache control
            addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            addHeader("Pragma", "no-cache")
        }.withCors()
    }

    // ----------- small helper for CORS -----------
    private fun Response.withCors(): Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With")
        return this
    }
}
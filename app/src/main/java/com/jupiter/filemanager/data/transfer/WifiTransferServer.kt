package com.jupiter.filemanager.data.transfer

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLConnection

/**
 * Pairing-gated local Relay server that exposes [rootDir] only to a desktop which holds the
 * in-memory [relaySession] token. The caller receives that token solely in the one-time pairing
 * link/QR; it is never written to disk.
 *
 * Backed by NanoHTTPD. Every request needs the short-lived pairing token in either the `pair`
 * query parameter or a `Bearer` authorization header. The root path ("/") then returns a simple
 * HTML directory index linking to each file; any other path that resolves to a regular file inside
 * [rootDir] is streamed back as a chunked download.
 *
 * All IO is guarded so a malformed request or filesystem error never crashes the
 * process — failures degrade to an HTTP error response or a null start result.
 */
class WifiTransferServer(
    port: Int = 0,
    private val rootDir: File,
    private val relaySession: RelaySession,
) : NanoHTTPD(port) {

    /**
     * Starts listening and returns the one-time reachable pairing URL (for example,
     * `http://192.168.1.5:49152/?pair=…`) or `null` if the socket could not be bound or no LAN
     * address was found. Port zero asks the OS for an ephemeral port, avoiding a predictable,
     * permanently advertised local endpoint.
     */
    fun startServer(): String? {
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            val ip = localIpAddress() ?: run {
                stopServer()
                return null
            }
            "http://$ip:$listeningPort/?pair=${java.net.URLEncoder.encode(relaySession.token, "UTF-8")}"
        } catch (t: Throwable) {
            try {
                stopServer()
            } catch (_: Throwable) {
                // ignore secondary failure while cleaning up
            }
            null
        }
    }

    /** Stops the server, swallowing any shutdown error. */
    fun stopServer() {
        try {
            stop()
        } catch (_: Throwable) {
            // ignore — already stopped or never started
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val now = System.currentTimeMillis()
            if (relaySession.isExpired(now)) {
                return errorResponse(Response.Status.UNAUTHORIZED, "This Relay session has expired. Start a new pairing session on your phone.")
            }
            if (!relaySession.isAuthorized(pairingToken(session), now)) {
                return unauthorizedResponse()
            }
            val rawUri = session.uri ?: "/"
            val decoded = try {
                java.net.URLDecoder.decode(rawUri, "UTF-8")
            } catch (_: Throwable) {
                rawUri
            }

            if (decoded == "/" || decoded.isEmpty()) {
                return directoryIndexResponse()
            }

            val target = resolveSafely(decoded)
            if (target == null || !target.exists()) {
                return errorResponse(Response.Status.NOT_FOUND, "Not found")
            }
            if (target.isDirectory) {
                // Only the root directory is browsable in this simple server.
                return errorResponse(Response.Status.FORBIDDEN, "Directory listing not allowed")
            }

            streamFile(target)
        } catch (t: Throwable) {
            errorResponse(Response.Status.INTERNAL_ERROR, "Server error")
        }
    }

    /** Builds an HTML index of the regular files directly inside [rootDir] after pairing. */
    private fun directoryIndexResponse(): Response {
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        builder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        builder.append("<meta name=\"referrer\" content=\"no-referrer\">")
        builder.append("<title>Jupiscan Relay</title></head><body>")
        builder.append("<h1>Jupiscan Relay</h1>")
        builder.append("<p>This paired session expires automatically. Stop it on your phone when you are done.</p>")

        val files = try {
            rootDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }
        } catch (_: Throwable) {
            null
        }

        if (files.isNullOrEmpty()) {
            builder.append("<p>No files available.</p>")
        } else {
            builder.append("<ul>")
            val encodedToken = try {
                java.net.URLEncoder.encode(relaySession.token, "UTF-8")
            } catch (_: Throwable) {
                relaySession.token
            }
            for (file in files) {
                val encodedName = try {
                    java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                } catch (_: Throwable) {
                    file.name
                }
                val displayName = escapeHtml(file.name)
                val size = humanReadableSize(file.length())
                builder.append("<li><a href=\"/")
                builder.append(encodedName)
                // Relative links do not preserve the root URL's query string. Carry the
                // short-lived token forward so a successfully paired browser can actually
                // fetch a listed file while unpaired requests still receive 401.
                builder.append("?pair=")
                builder.append(encodedToken)
                builder.append("\">")
                builder.append(displayName)
                builder.append("</a> (")
                builder.append(size)
                builder.append(")</li>")
            }
            builder.append("</ul>")
        }

        builder.append("</body></html>")
        return secureResponse(newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", builder.toString()))
    }

    /** Streams [file] as a chunked download with a best-effort MIME type. */
    private fun streamFile(file: File): Response {
        val mime = guessMimeType(file)
        val stream = FileInputStream(file)
        val response = newChunkedResponse(Response.Status.OK, mime, stream)
        response.addHeader(
            "Content-Disposition",
            "attachment; filename=\"${file.name.replace("\"", "")}\"",
        )
        return secureResponse(response)
    }

    /**
     * Resolves a request path to a file strictly inside [rootDir], rejecting any
     * path-traversal that would escape the served directory.
     */
    private fun resolveSafely(uriPath: String): File? {
        return try {
            val relative = uriPath.trimStart('/')
            val candidate = File(rootDir, relative).canonicalFile
            val base = rootDir.canonicalFile
            if (candidate.path == base.path || candidate.path.startsWith(base.path + File.separator)) {
                candidate
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun guessMimeType(file: File): String {
        return try {
            URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
        } catch (_: Throwable) {
            "application/octet-stream"
        }
    }

    private fun pairingToken(session: IHTTPSession): String? {
        val authorization = session.headers["authorization"]
        val bearer = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
        return bearer ?: session.parameters[PAIRING_PARAMETER]?.firstOrNull()
    }

    private fun unauthorizedResponse(): Response = secureResponse(
        newFixedLengthResponse(
            Response.Status.UNAUTHORIZED,
            "text/plain; charset=utf-8",
            "Pair this browser from the Jupiscan Relay QR code before accessing files.",
        ).apply { addHeader("WWW-Authenticate", "Bearer") },
    )

    private fun errorResponse(status: Response.Status, message: String): Response {
        return secureResponse(newFixedLengthResponse(status, "text/plain; charset=utf-8", message))
    }

    /** Prevent paired links, filenames and local responses from being retained by a browser cache. */
    private fun secureResponse(response: Response): Response = response.apply {
        addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
        addHeader("Pragma", "no-cache")
        addHeader("Referrer-Policy", "no-referrer")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    private fun humanReadableSize(bytes: Long): String {
        if (bytes < 0) return "?"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var idx = 0
        while (value >= 1024.0 && idx < units.size - 1) {
            value /= 1024.0
            idx++
        }
        return String.format("%.1f %s", value, units[idx])
    }

    private fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private companion object {
        const val PAIRING_PARAMETER = "pair"
        /** Returns the first non-loopback site-local IPv4 address, or null. */
        fun localIpAddress(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                for (nif in interfaces) {
                    try {
                        if (!nif.isUp || nif.isLoopback) continue
                    } catch (_: Throwable) {
                        continue
                    }
                    val addresses = nif.inetAddresses ?: continue
                    for (addr in addresses) {
                        if (addr.isLoopbackAddress) continue
                        if (addr is Inet4Address && addr.isSiteLocalAddress) {
                            return addr.hostAddress
                        }
                    }
                }
                // Fallback: any non-loopback IPv4 if no site-local found.
                val again = NetworkInterface.getNetworkInterfaces() ?: return null
                for (nif in again) {
                    try {
                        if (!nif.isUp || nif.isLoopback) continue
                    } catch (_: Throwable) {
                        continue
                    }
                    val addresses = nif.inetAddresses ?: continue
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
                null
            } catch (_: Throwable) {
                null
            }
        }
    }
}

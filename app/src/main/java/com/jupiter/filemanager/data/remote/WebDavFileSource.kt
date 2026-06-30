package com.jupiter.filemanager.data.remote

import android.util.Xml
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.remote.RemoteCredentials
import com.jupiter.filemanager.domain.remote.RemoteFileSource
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

/**
 * WebDAV [RemoteFileSource] implemented with OkHttp.
 *
 * Directory listings use a PROPFIND request with `Depth: 1` and HTTP Basic
 * authentication, parsing the multistatus XML response defensively with a pull
 * parser. Downloads stream the response body to disk. All blocking IO runs on
 * the injected IO dispatcher and every failure is converted into an
 * [AppResult.Failure] carrying an [AppError.Io] so a network error can never
 * crash the app.
 */
@Singleton
class WebDavFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteFileSource {

    override val type: ConnectionType = ConnectionType.WEBDAV

    private val client: OkHttpClient = OkHttpClient.Builder().build()

    private companion object {
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""
    }

    override suspend fun testConnection(credentials: RemoteCredentials): AppResult<Unit> =
        withContext(io) {
            try {
                val url = buildUrl(credentials, basePathRelative(credentials))
                val request = propfindRequest(url, credentials)
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        AppResult.Success(Unit)
                    } else {
                        AppResult.Failure(
                            AppError.Io("WebDAV connection failed: HTTP ${response.code}"),
                        )
                    }
                }
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Io("WebDAV connection failed", t))
            }
        }

    override suspend fun list(
        credentials: RemoteCredentials,
        path: String,
    ): AppResult<List<RemoteEntry>> = withContext(io) {
        try {
            val requestPath = joinPaths(basePathRelative(credentials), path)
            val url = buildUrl(credentials, requestPath)
            val request = propfindRequest(url, credentials)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError.Io("WebDAV list failed: HTTP ${response.code}"),
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext AppResult.Failure(
                        AppError.Io("WebDAV list failed: empty response body"),
                    )
                val basePrefix = normalizeBaseForCompare(credentials)
                val entries = parseMultiStatus(body, basePrefix)
                AppResult.Success(entries)
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io("WebDAV list failed", t))
        }
    }

    override suspend fun download(
        credentials: RemoteCredentials,
        remotePath: String,
        destination: File,
    ): AppResult<Unit> = withContext(io) {
        try {
            val requestPath = joinPaths(basePathRelative(credentials), remotePath)
            val url = buildUrl(credentials, requestPath)
            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    val auth = basicAuthOrNull(credentials)
                    if (auth != null) header("Authorization", auth)
                }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError.Io("WebDAV download failed: HTTP ${response.code}"),
                    )
                }
                val body = response.body
                    ?: return@withContext AppResult.Failure(
                        AppError.Io("WebDAV download failed: empty response body"),
                    )
                destination.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                AppResult.Success(Unit)
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io("WebDAV download failed", t))
        }
    }

    // ----- request building -----------------------------------------------------

    private fun propfindRequest(url: String, credentials: RemoteCredentials): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .method(
                "PROPFIND",
                PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()),
            )
        val auth = basicAuthOrNull(credentials)
        if (auth != null) builder.header("Authorization", auth)
        return builder.build()
    }

    private fun basicAuthOrNull(credentials: RemoteCredentials): String? {
        val user = credentials.username
        return if (!user.isNullOrEmpty()) {
            Credentials.basic(user, credentials.password ?: "")
        } else {
            null
        }
    }

    /**
     * Resolves the scheme. WebDAV is HTTPS by default: deriving https solely from
     * port == 443 would force HTTPS servers on custom ports (e.g. 8443) down to
     * plaintext http and break them. [RemoteCredentials] carries no explicit TLS
     * flag, so we treat https as the default and only fall back to plaintext http
     * for the well-known cleartext port 80.
     */
    private fun scheme(credentials: RemoteCredentials): String =
        if (credentials.port == 80) "http" else "https"

    /** Builds an absolute URL from host(+port) and an already-relative path. */
    private fun buildUrl(credentials: RemoteCredentials, relativePath: String): String {
        val scheme = scheme(credentials)
        val port = credentials.port
        val includePort = port != 0 &&
            !(scheme == "http" && port == 80) &&
            !(scheme == "https" && port == 443)
        val authority = if (includePort) "${credentials.host}:$port" else credentials.host
        val encodedPath = encodePath(relativePath)
        val pathPart = if (encodedPath.startsWith("/")) encodedPath else "/$encodedPath"
        return "$scheme://$authority$pathPart"
    }

    private fun basePathRelative(credentials: RemoteCredentials): String =
        credentials.shareOrBasePath ?: ""

    /** Joins a base path and a sub-path into a single normalized relative path. */
    private fun joinPaths(base: String, sub: String): String {
        val b = base.trim('/')
        val s = sub.trim('/')
        return when {
            b.isEmpty() && s.isEmpty() -> "/"
            b.isEmpty() -> "/$s"
            s.isEmpty() -> "/$b"
            else -> "/$b/$s"
        }
    }

    /** Percent-encodes each path segment while preserving slashes. */
    private fun encodePath(path: String): String {
        if (path.isEmpty()) return "/"
        val leading = if (path.startsWith("/")) "/" else ""
        val trailing = if (path.length > 1 && path.endsWith("/")) "/" else ""
        val segments = path.trim('/').split('/')
        val encoded = segments.joinToString("/") { segment ->
            if (segment.isEmpty()) "" else encodeSegment(segment)
        }
        return leading + encoded + trailing
    }

    private fun encodeSegment(segment: String): String {
        val builder = StringBuilder()
        for (ch in segment) {
            if (ch.isLetterOrDigit() || ch in "-_.~") {
                builder.append(ch)
            } else {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                    builder.append('%')
                    builder.append("%02X".format(b.toInt() and 0xFF))
                }
            }
        }
        return builder.toString()
    }

    /** The base prefix (decoded, no port) used to strip server-absolute hrefs down to paths. */
    private fun normalizeBaseForCompare(credentials: RemoteCredentials): String =
        "/" + basePathRelative(credentials).trim('/')

    // ----- XML parsing ----------------------------------------------------------

    private fun parseMultiStatus(xml: String, basePrefix: String): List<RemoteEntry> {
        val results = mutableListOf<RemoteEntry>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var href: String? = null
        var displayName: String? = null
        var contentLength: Long = 0L
        var lastModified: Long = 0L
        var isDirectory = false
        var inResponse = false
        var inResourceType = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName(parser.name)) {
                        "response" -> {
                            inResponse = true
                            href = null
                            displayName = null
                            contentLength = 0L
                            lastModified = 0L
                            isDirectory = false
                        }

                        "href" -> if (inResponse) href = safeText(parser)
                        "displayname" -> if (inResponse) displayName = safeText(parser)
                        "getcontentlength" -> if (inResponse) {
                            contentLength = safeText(parser)?.trim()?.toLongOrNull() ?: 0L
                        }

                        "getlastmodified" -> if (inResponse) {
                            lastModified = parseHttpDate(safeText(parser))
                        }

                        "resourcetype" -> if (inResponse) inResourceType = true
                        "collection" -> if (inResourceType) isDirectory = true
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (localName(parser.name)) {
                        "resourcetype" -> inResourceType = false
                        "response" -> {
                            if (inResponse) {
                                val rawPath = href
                                val entry = rawPath?.let {
                                    buildEntry(
                                        it,
                                        displayName,
                                        contentLength,
                                        lastModified,
                                        isDirectory,
                                        basePrefix,
                                    )
                                }
                                if (entry != null && !isSelf(entry.path, basePrefix)) {
                                    results.add(entry)
                                }
                            }
                            inResponse = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun buildEntry(
        rawHref: String,
        displayName: String?,
        size: Long,
        lastModified: Long,
        isDirectory: Boolean,
        basePrefix: String,
    ): RemoteEntry? {
        val decodedPath = hrefToPath(rawHref) ?: return null
        // The server returns server-absolute hrefs that include the WebDAV base
        // path. Strip the base so the entry path is RELATIVE to the connection's
        // base — exactly the form list()/download() accept (they re-join the base
        // via joinPaths). Returning the full server-absolute path here would make
        // those callers double-prepend the base and 404.
        val relative = stripBasePrefix(decodedPath, basePrefix)
        val normalized = relative.trimEnd('/').ifEmpty { "/" }
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: normalized.substringAfterLast('/').ifEmpty { normalized }
        return RemoteEntry(
            name = name,
            path = normalized,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else size,
            lastModified = lastModified,
        )
    }

    /**
     * Removes [basePrefix] from the front of a server-absolute [path], yielding a
     * path relative to the connection base. If [path] is not under the base (it
     * already looks relative, or the prefix is the root) it is returned unchanged
     * apart from leading-slash normalization.
     */
    private fun stripBasePrefix(path: String, basePrefix: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val base = basePrefix.trimEnd('/')
        if (base.isEmpty() || base == "/") return normalizedPath
        return when {
            normalizedPath == base -> "/"
            normalizedPath.startsWith("$base/") -> normalizedPath.substring(base.length)
            else -> normalizedPath
        }
    }

    /** Extracts the decoded path component from an href (which may be an absolute URL). */
    private fun hrefToPath(href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        val pathOnly = if (trimmed.contains("://")) {
            val afterScheme = trimmed.substringAfter("://")
            val slash = afterScheme.indexOf('/')
            if (slash >= 0) afterScheme.substring(slash) else "/"
        } else {
            trimmed
        }
        return try {
            URLDecoder.decode(pathOnly, "UTF-8")
        } catch (_: Throwable) {
            pathOnly
        }
    }

    /**
     * True when [path] refers to the requested directory itself (so it's excluded).
     *
     * Entry paths are now relative to the connection base, so the self entry
     * collapses to the root "/". We still compare against [basePrefix] defensively
     * in case an href could not be stripped (e.g. it had no base segment).
     */
    private fun isSelf(path: String, basePrefix: String): Boolean {
        val a = path.trimEnd('/').ifEmpty { "/" }
        if (a == "/") return true
        val b = basePrefix.trimEnd('/').ifEmpty { "/" }
        return a == b
    }

    private fun parseHttpDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: Throwable) {
            0L
        }
    }

    /** Returns the text content of the current element, or null on any parse error. */
    private fun safeText(parser: XmlPullParser): String? = try {
        if (parser.next() == XmlPullParser.TEXT) parser.text else null
    } catch (_: Throwable) {
        null
    }

    /** Strips any namespace prefix from a tag name. */
    private fun localName(name: String?): String? =
        name?.substringAfterLast(':')
}

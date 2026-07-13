package com.jupiter.filemanager.core.util

import android.webkit.MimeTypeMap
import com.jupiter.filemanager.domain.model.FileType
import java.util.Locale

/**
 * Utilities for deriving file extensions, MIME types, and high-level [FileType]
 * classifications from file names.
 *
 * All lookups are pure, allocation-light, and safe to call off the main thread.
 * Extension comparisons are performed in lowercase using [Locale.US] to avoid
 * locale-specific casing surprises (e.g. the Turkish dotless 'i').
 */

// region Extension groups -----------------------------------------------------

private val IMAGE_EXTENSIONS: Set<String> = setOf(
    "jpg", "jpeg", "jpe", "jfif", "png", "gif", "bmp", "webp", "heic", "heif",
    "tif", "tiff", "svg", "ico", "raw", "dng", "cr2", "nef", "arw", "orf",
    "rw2", "psd", "ai", "avif", "jp2",
)

private val VIDEO_EXTENSIONS: Set<String> = setOf(
    "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v", "3gp",
    "3g2", "mpeg", "mpg", "mpe", "mts", "m2ts", "ts", "vob", "ogv", "rm",
    "rmvb", "divx", "asf",
)

private val AUDIO_EXTENSIONS: Set<String> = setOf(
    "mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "m4b", "wma", "opus",
    "aiff", "aif", "amr", "mid", "midi", "ape", "ac3", "dts", "mka", "ra",
)

private val PDF_EXTENSIONS: Set<String> = setOf(
    "pdf",
)

private val DOCUMENT_EXTENSIONS: Set<String> = setOf(
    // Word processing
    "doc", "docx", "dot", "dotx", "odt", "rtf", "wpd", "pages",
    // Spreadsheets
    "xls", "xlsx", "xlsm", "xlt", "xltx", "ods", "csv", "tsv", "numbers",
    // Presentations
    "ppt", "pptx", "pps", "ppsx", "pot", "potx", "odp", "key",
    // Plain text / notes
    "txt", "text", "log", "md", "markdown", "rst", "tex", "epub", "mobi",
    "azw", "azw3", "djvu", "fb2", "ics", "vcf",
)

private val ARCHIVE_EXTENSIONS: Set<String> = setOf(
    "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz",
    "txz", "lz", "lzma", "z", "cab", "iso", "jar", "war", "ear", "deb",
    "rpm", "arj", "lha", "lzh", "zst", "zstd",
)

private val APK_EXTENSIONS: Set<String> = setOf(
    "apk", "apks", "apkm", "xapk", "aab",
)

private val CODE_EXTENSIONS: Set<String> = setOf(
    // JVM / Android
    "kt", "kts", "java", "class", "gradle", "scala", "groovy", "clj",
    // Web
    "js", "mjs", "cjs", "jsx", "ts", "tsx", "html", "htm", "xhtml", "css",
    "scss", "sass", "less", "vue", "svelte",
    // Systems
    "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "cs", "go", "rs",
    "swift", "m", "mm", "objc",
    // Scripting
    "py", "pyw", "rb", "php", "phtml", "pl", "pm", "lua", "sh", "bash",
    "zsh", "fish", "bat", "cmd", "ps1", "r", "dart", "ex", "exs", "erl",
    "hs", "elm", "jl", "nim", "cr", "vala",
    // Data / config / markup
    "json", "json5", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf",
    "properties", "env", "sql", "graphql", "gql", "proto", "asm", "s",
)

// endregion

// region MIME fallback table --------------------------------------------------

/**
 * Fallback extension -> MIME type table used when the platform
 * [MimeTypeMap] does not have an entry for a given extension. Keys are
 * lowercase extensions without a leading dot.
 */
private val MIME_FALLBACKS: Map<String, String> = mapOf(
    // Images
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "jpe" to "image/jpeg",
    "jfif" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "bmp" to "image/bmp",
    "webp" to "image/webp",
    "heic" to "image/heic",
    "heif" to "image/heif",
    "tif" to "image/tiff",
    "tiff" to "image/tiff",
    "svg" to "image/svg+xml",
    "ico" to "image/x-icon",
    "avif" to "image/avif",
    "dng" to "image/x-adobe-dng",
    "psd" to "image/vnd.adobe.photoshop",
    "jp2" to "image/jp2",
    // Video
    "mp4" to "video/mp4",
    "m4v" to "video/x-m4v",
    "mkv" to "video/x-matroska",
    "webm" to "video/webm",
    "avi" to "video/x-msvideo",
    "mov" to "video/quicktime",
    "wmv" to "video/x-ms-wmv",
    "flv" to "video/x-flv",
    "3gp" to "video/3gpp",
    "3g2" to "video/3gpp2",
    "mpeg" to "video/mpeg",
    "mpg" to "video/mpeg",
    "ts" to "video/mp2t",
    "m2ts" to "video/mp2t",
    "ogv" to "video/ogg",
    // Audio
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "flac" to "audio/flac",
    "aac" to "audio/aac",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/opus",
    "m4a" to "audio/mp4",
    "m4b" to "audio/mp4",
    "wma" to "audio/x-ms-wma",
    "aiff" to "audio/aiff",
    "aif" to "audio/aiff",
    "amr" to "audio/amr",
    "mid" to "audio/midi",
    "midi" to "audio/midi",
    "ape" to "audio/x-ape",
    "mka" to "audio/x-matroska",
    // Documents
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "dot" to "application/msword",
    "dotx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
    "odt" to "application/vnd.oasis.opendocument.text",
    "rtf" to "application/rtf",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "xlsm" to "application/vnd.ms-excel.sheet.macroEnabled.12",
    "ods" to "application/vnd.oasis.opendocument.spreadsheet",
    "csv" to "text/csv",
    "tsv" to "text/tab-separated-values",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "pps" to "application/vnd.ms-powerpoint",
    "ppsx" to "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "odp" to "application/vnd.oasis.opendocument.presentation",
    "txt" to "text/plain",
    "text" to "text/plain",
    "log" to "text/plain",
    "md" to "text/markdown",
    "markdown" to "text/markdown",
    "rst" to "text/x-rst",
    "tex" to "application/x-tex",
    "epub" to "application/epub+zip",
    "mobi" to "application/x-mobipocket-ebook",
    "azw" to "application/vnd.amazon.ebook",
    "azw3" to "application/vnd.amazon.ebook",
    "djvu" to "image/vnd.djvu",
    "fb2" to "application/x-fictionbook+xml",
    "ics" to "text/calendar",
    "vcf" to "text/vcard",
    // Archives
    "zip" to "application/zip",
    "rar" to "application/vnd.rar",
    "7z" to "application/x-7z-compressed",
    "tar" to "application/x-tar",
    "gz" to "application/gzip",
    "tgz" to "application/gzip",
    "bz2" to "application/x-bzip2",
    "tbz" to "application/x-bzip2",
    "tbz2" to "application/x-bzip2",
    "xz" to "application/x-xz",
    "txz" to "application/x-xz",
    "lz" to "application/x-lzip",
    "lzma" to "application/x-lzma",
    "z" to "application/x-compress",
    "cab" to "application/vnd.ms-cab-compressed",
    "iso" to "application/x-iso9660-image",
    "jar" to "application/java-archive",
    "war" to "application/java-archive",
    "ear" to "application/java-archive",
    "deb" to "application/vnd.debian.binary-package",
    "rpm" to "application/x-rpm",
    "arj" to "application/x-arj",
    "zst" to "application/zstd",
    "zstd" to "application/zstd",
    // Apps
    "apk" to "application/vnd.android.package-archive",
    "apks" to "application/vnd.android.package-archive",
    "xapk" to "application/vnd.android.package-archive",
    "apkm" to "application/vnd.android.package-archive",
    "aab" to "application/x-authorware-bin",
    // Code / config
    "kt" to "text/x-kotlin",
    "kts" to "text/x-kotlin",
    "java" to "text/x-java-source",
    "class" to "application/java-vm",
    "gradle" to "text/plain",
    "scala" to "text/x-scala",
    "groovy" to "text/x-groovy",
    "js" to "text/javascript",
    "mjs" to "text/javascript",
    "cjs" to "text/javascript",
    "jsx" to "text/jsx",
    "ts" to "text/typescript",
    "tsx" to "text/tsx",
    "html" to "text/html",
    "htm" to "text/html",
    "xhtml" to "application/xhtml+xml",
    "css" to "text/css",
    "scss" to "text/x-scss",
    "sass" to "text/x-sass",
    "less" to "text/x-less",
    "c" to "text/x-c",
    "h" to "text/x-c",
    "cpp" to "text/x-c++src",
    "cc" to "text/x-c++src",
    "cxx" to "text/x-c++src",
    "hpp" to "text/x-c++hdr",
    "cs" to "text/x-csharp",
    "go" to "text/x-go",
    "rs" to "text/x-rust",
    "swift" to "text/x-swift",
    "py" to "text/x-python",
    "pyw" to "text/x-python",
    "rb" to "text/x-ruby",
    "php" to "application/x-httpd-php",
    "pl" to "text/x-perl",
    "lua" to "text/x-lua",
    "sh" to "application/x-sh",
    "bash" to "application/x-sh",
    "zsh" to "application/x-sh",
    "bat" to "application/x-bat",
    "cmd" to "application/x-bat",
    "ps1" to "application/x-powershell",
    "r" to "text/x-r",
    "dart" to "application/dart",
    "json" to "application/json",
    "json5" to "application/json5",
    "xml" to "text/xml",
    "yml" to "application/x-yaml",
    "yaml" to "application/x-yaml",
    "toml" to "application/toml",
    "ini" to "text/plain",
    "cfg" to "text/plain",
    "conf" to "text/plain",
    "properties" to "text/x-java-properties",
    "env" to "text/plain",
    "sql" to "application/sql",
    "graphql" to "application/graphql",
    "gql" to "application/graphql",
    "proto" to "text/plain",
    "asm" to "text/x-asm",
)

// endregion

/**
 * Returns the lowercase extension of [name] without the leading dot, or an empty
 * string when the name has no extension.
 *
 * Only the substring after the final dot is considered, and leading dots (as in
 * dotfiles such as ".gitignore") are not treated as an extension separator.
 */
fun extensionOf(name: String): String {
    val trimmed = name.substringAfterLast('/').trim()
    if (trimmed.isEmpty()) return ""
    val dotIndex = trimmed.lastIndexOf('.')
    // No dot, leading dot (dotfile), or trailing dot -> no extension.
    if (dotIndex <= 0 || dotIndex == trimmed.length - 1) return ""
    return trimmed.substring(dotIndex + 1).lowercase(Locale.US)
}

/**
 * Resolves a MIME type for [name] using the platform [MimeTypeMap] first, then
 * falling back to an internal extension table. Returns null when the type is
 * unknown.
 */
fun mimeTypeFor(name: String): String? {
    val extension = extensionOf(name)
    if (extension.isEmpty()) return null
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
    return MIME_FALLBACKS[extension]
}

/**
 * Classifies a file name into a high-level [FileType]. Directories always map to
 * [FileType.FOLDER]. Files are matched by extension group, falling back to
 * [FileType.OTHER] when no group matches.
 */
fun fileTypeFor(name: String, isDirectory: Boolean, mimeType: String? = null): FileType {
    if (isDirectory) return FileType.FOLDER
    val extension = extensionOf(name)
    val fromExtension = when (extension) {
        in IMAGE_EXTENSIONS -> FileType.IMAGE
        in VIDEO_EXTENSIONS -> FileType.VIDEO
        in AUDIO_EXTENSIONS -> FileType.AUDIO
        in PDF_EXTENSIONS -> FileType.PDF
        in APK_EXTENSIONS -> FileType.APK
        in ARCHIVE_EXTENSIONS -> FileType.ARCHIVE
        in CODE_EXTENSIONS -> FileType.CODE
        in DOCUMENT_EXTENSIONS -> FileType.DOCUMENT
        else -> FileType.OTHER
    }
    return if (fromExtension != FileType.OTHER) fromExtension else fileTypeForMime(mimeType)
}

/**
 * MediaStore knows a file's MIME type even when its display name has no extension. Keep that
 * provider signal as a fallback so extensionless camera/download images still enter the image
 * fingerprint pipeline rather than being permanently misclassified as [FileType.OTHER].
 */
fun fileTypeForMime(mimeType: String?): FileType {
    val mime = mimeType?.trim()?.lowercase(Locale.US).orEmpty()
    return when {
        mime.startsWith("image/") -> FileType.IMAGE
        mime.startsWith("video/") -> FileType.VIDEO
        mime.startsWith("audio/") -> FileType.AUDIO
        mime == "application/pdf" -> FileType.PDF
        mime == "application/vnd.android.package-archive" -> FileType.APK
        mime.startsWith("text/") -> FileType.CODE
        mime == "application/msword" ||
            mime.startsWith("application/vnd.openxmlformats-officedocument") ||
            mime.startsWith("application/vnd.oasis.opendocument") ||
            mime == "application/rtf" -> FileType.DOCUMENT
        else -> FileType.OTHER
    }
}

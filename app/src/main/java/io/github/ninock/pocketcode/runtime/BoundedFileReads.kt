package io.github.ninock.pocketcode.runtime

import java.io.File
import java.io.RandomAccessFile

/** Reads at most [maxBytes] from the end of a file without allocating for the whole file. */
internal fun File.readTailText(maxBytes: Int): String {
    if (!isFile || maxBytes <= 0) return ""
    return RandomAccessFile(this, "r").use { input ->
        val byteCount = minOf(input.length(), maxBytes.toLong()).toInt()
        input.seek(input.length() - byteCount)
        val bytes = ByteArray(byteCount)
        input.readFully(bytes)
        bytes.toString(Charsets.UTF_8)
    }
}

/** Reads at most [maxBytes] from the start of a file without allocating for the whole file. */
internal fun File.readHeadText(maxBytes: Int): String {
    if (!isFile || maxBytes <= 0) return ""
    return RandomAccessFile(this, "r").use { input ->
        val bytes = ByteArray(minOf(input.length(), maxBytes.toLong()).toInt())
        input.readFully(bytes)
        bytes.toString(Charsets.UTF_8)
    }
}

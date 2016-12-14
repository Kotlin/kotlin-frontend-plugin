package org.jetbrains.kotlin.gradle.frontend.util

import java.io.*
import java.nio.file.*
import java.nio.file.attribute.*

class LogTail(val file: () -> Path, val pollPeriodMillis: Long = 75, val timeout: Long = 1000) {
    private var logPosition = 0L
    private var logCreationDate = 0L

    fun rememberLogStartPosition() {
        try {
            // it is important to execute it BEFORE any task run because we need to remember file position
            // before any changes detected
            val attrs = Files.readAttributes(file(), BasicFileAttributes::class.java)

            logCreationDate = attrs.creationTime().toMillis()
            logPosition = Math.max(logPosition, attrs.size())
        } catch (ignore: IOException) {
        }
    }

    fun dumpLog() {
        try {
            var lastCheck = System.currentTimeMillis()

            while (true) {
                val attributes = Files.readAttributes(file(), BasicFileAttributes::class.java)
                val size = attributes.size()
                val date = attributes.creationTime().toMillis()

                if (size != logPosition || date != logCreationDate) {
                    lastCheck = System.currentTimeMillis()

                    Files.newInputStream(file()).use { s ->
                        val bytesCount = if (date == logCreationDate && size >= logPosition) {
                            if (s.skip(logPosition) != logPosition) {
                                0   // truncated during read
                            } else {
                                size - logPosition
                            }
                        } else {
                            size
                        }

                        val bytes = s.readFully(bytesCount.toInt())
                        System.err.write(bytes)
                        System.err.flush()
                    }

                    logPosition = size
                    logCreationDate = date
                } else if (System.currentTimeMillis() - lastCheck > timeout) {
                    break
                }

                Thread.sleep(pollPeriodMillis)
            }
        } catch (ignore: IOException) {
        }
    }

    private fun InputStream.readFully(size: Int): ByteArray {
        val buffer = ByteArray(size)
        var read = 0

        do {
            val rc = read(buffer, read, size - read)
            if (rc == -1) {
                throw IOException("Unexpected EOF")
            }

            read += rc
        } while (read < size)

        return buffer
    }
}
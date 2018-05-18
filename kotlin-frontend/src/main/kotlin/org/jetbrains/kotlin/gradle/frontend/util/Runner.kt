package org.jetbrains.kotlin.gradle.frontend.util

import net.rubygrapefruit.platform.*
import org.apache.tools.ant.taskdefs.condition.*
import org.gradle.api.*
import org.jetbrains.kotlin.utils.*
import java.io.*
import java.nio.*
import java.util.concurrent.*

fun ProcessBuilder.startWithRedirectOnFail(project: Project, name: String, exec: Executor = DummyExecutor): java.lang.Process {
    require(command().isNotEmpty()) { "No command specified" }

    val cmd = command().toList()
    val process = Native.get(ProcessLauncher::class.java).let { l ->
        addCommandPathToSystemPath()

        if (Os.isFamily(Os.FAMILY_WINDOWS) && !cmd[0].endsWith(".exe")) {
            command(listOf("cmd.exe", "/c") + cmd)
        }

        l.start(this)!!
    }

    val out = if (project.logger.isInfoEnabled) System.out else NullOutputStream
    val buffered = OutputStreamWithBuffer(out, 8192)

    val rc = try {
        ProcessHandler(process, buffered, buffered, exec).startAndWaitFor()
    } catch (t: Throwable) {
        project.logger.error("Process ${command().first()} failed", t)
        process.destroyForcibly()
        -1
    }

    if (rc != 0) {
        project.logger.error(buffered.lines().toString(Charsets.UTF_8))

        project.logger.debug("Command failed (exit code = $rc): ${command().joinToString(" ")}")
        throw GradleException("$name failed (exit code = $rc)")
    }

    return process
}

private object DummyExecutor : Executor {
    override fun execute(command: Runnable) {
        Thread(command).start()
    }
}

private object NullOutputStream : OutputStream() {
    override fun write(b: ByteArray?) {
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
    }

    override fun write(b: Int) {
    }
}

internal class OutputStreamWithBuffer(out: OutputStream, sizeLimit: Int) : FilterOutputStream(out) {
    private val buffer = ByteBuffer.allocate(sizeLimit)

    @Synchronized
    override fun write(b: Int) {
        if (ensure(1) >= 1) {
            buffer.put(b.toByte())
        }
        out.write(b)
    }


    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        putOrRoll(b, off, len)
        out.write(b, off, len)
    }

    @Synchronized
    fun lines(): ByteArray = buffer.duplicate().let { it.flip(); ByteArray(it.remaining()).apply { it.get(this) } }

    private fun putOrRoll(b: ByteArray, off: Int, len: Int) {
        var pos = off
        var rem = len

        while (rem > 0) {
            val count = ensure(rem)
            buffer.put(b, pos, count)
            pos += count
            rem -= count
        }
    }

    private fun ensure(count: Int): Int {
        if (buffer.remaining() < count) {
            val space = buffer.remaining()

            buffer.flip()
            while (buffer.hasRemaining() && buffer.position() + space < count) {
                dropLine()
            }
            buffer.compact()
        }

        return Math.min(count, buffer.remaining())
    }

    private fun dropLine() {
        while (buffer.hasRemaining()) {
            if (buffer.get().toInt() == 0x0d) {
                break
            }
        }
    }
}

private class ProcessHandler(val process: java.lang.Process, private val out: OutputStream, private val err: OutputStream, private val exec: Executor) {
    private val latch = CountDownLatch(1)
    private var exitCode: Int = 0
    private var exception: Throwable? = null

    fun start() {
        StreamForwarder(process.inputStream, out, exec).start()
        StreamForwarder(process.errorStream, err, exec).start()

        exec.execute {
            try {
                exitCode = process.waitFor()
            } catch (t: Throwable) {
                exception = t
            } finally {
                closeQuietly(process.inputStream)
                closeQuietly(process.errorStream)
                closeQuietly(process.outputStream)

                latch.countDown()
            }
        }
    }

    fun waitFor(): Int {
        latch.await()

        exception?.let { throw it }

        return exitCode
    }

    fun startAndWaitFor(): Int {
        start()
        return waitFor()
    }
}

private class StreamForwarder(val source: InputStream, val destination: OutputStream, val exec: Executor) {
    fun start() {
        exec.execute {
            try {
                val buffer = ByteArray(4096)
                do {
                    val rc = source.read(buffer)
                    if (rc == -1) {
                        break
                    }

                    destination.write(buffer, 0, rc)
                    if (source.available() == 0) {
                        destination.flush()
                    }
                } while (true)
            } catch (ignore: IOException) {
            }
            destination.flush()
        }
    }
}
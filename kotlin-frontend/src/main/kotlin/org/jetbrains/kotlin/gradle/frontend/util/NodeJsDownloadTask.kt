package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.internal.logging.progress.*
import org.jetbrains.kotlin.preprocessor.*
import java.io.*
import java.math.*
import java.net.*
import java.nio.file.*
import java.util.concurrent.*
import kotlin.concurrent.*

open class NodeJsDownloadTask : DefaultTask() {
    @get:Input
    var mirror: String = "https://nodejs.org/dist"

    @get:Input
    var version: String = "7.9.0"

    @get:OutputFile
    var nodePathTextFile: File = project.buildDir.resolve("nodePath.txt")

    @TaskAction
    fun doDownload() {
        if (version == "latest") {
            version = detectLatest()
        }

        val url = nodeUrl(version)

        val outFile = project.buildDir.resolve(url.path.substringAfterLast('/'))
        outFile.parentFile.mkdirsOrFail()

        download(url, outFile)
        val nodejsDir = extract(outFile)

        val executable = arrayOf("bin/node.exe", "bin/node")
                .map { nodejsDir.resolve(it) }
                .firstOrNull { it.exists() } ?: throw GradleException("No node executable found in $nodejsDir")

        project.tasks.create("ask-nodejs-version", Exec::class.java) { execute ->
            execute.executable(executable.absoluteFile).args("--version")
        }.execute()

        nodePathTextFile.writeText(nodejsDir.absolutePath)
    }

    private fun detectLatest(): String {
        val url = URL("${mirror()}/latest/SHASUMS256.txt")
        val op = newOperation().apply {
            loggingHeader = "Get latest version from $url..."
            description = "Get latest version"
        }
        op.started("Connecting...")
        val nodeVersionExpression = "node-v([\\d.]+)-([a-zA-Z0-9]+)-([a-zA-Z0-9]+)\\.(tar.gz|tar.xz|zip|msi|7z)".toRegex()

        val versions = url.openStream().bufferedReader().use {
            op.progress("Downloading...")
            it.readLines().mapNotNull { nodeVersionExpression.find(it) }.map { it.groupValues[1] }.distinct()
        }

        val version = versions.firstOrNull() ?: throw GradleException("Failed to detect versions from")
        op.completed("Got $version")

        return version
    }

    private fun nodeUrl(version: String): URL {
        val name = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")

        val nodeArch = when (arch) {
            "x86" -> "x86"
            "i386" -> "x86"
            "i586" -> "x86"

            "x64" -> "x64"
            "x86_64" -> "x64"
            "amd64" -> "x64"
            else -> throw GradleException("Node doesn't support OS architecture $arch")
        }

        val nodeOsName = when {
            name.contains("Linux", ignoreCase = true) -> "linux"
            name.contains("SunOS", ignoreCase = true) -> "sunos"
            name.contains("Mac", ignoreCase = true) -> "darwin"
            name.contains("Windows", ignoreCase = true) -> "win"
            else -> throw GradleException("Node doesn't support OS $name")
        }

        val type = if (nodeOsName == "win") "zip" else "tar.gz"

        return nodeUrl(version, "$nodeOsName-$nodeArch", type)
    }

    private fun nodeUrl(version: String, system: String, type: String): URL {
        return URL("${mirror()}/v$version/node-v$version-$system.$type")
    }

    private fun download(from: URL, to: File) {
        val op = newOperation().apply {
            loggingHeader = "Downloading $from"
            description = "Downloading"
        }

        to.parentFile.mkdirsOrFail()
        FileOutputStream(to).use { fos ->
            val result = ArrayBlockingQueue<Any>(1)
            thread(start = true, name = "downloader") {
                op.started()

                op.progress("Connecting...")

                val c = from.openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15000
                    c.readTimeout = 15000
                    c.allowUserInteraction = false
                    c.useCaches = false
                    c.doInput = true

                    val total = c.contentLengthLong
                    var bytesRead = 0L

                    op.downloadProgress(total, 0)

                    c.inputStream.use { s ->
                        val buffer = ByteArray(8192)
                        while (bytesRead < total) {
                            val rc = s.read(buffer)
                            op.downloadProgress(total, bytesRead)

                            if (rc == -1) break
                            if (rc > 0) {
                                fos.write(buffer, 0, rc)
                            }

                            bytesRead += rc
                        }
                    }

                    op.completed("Completed.")
                    result.put("OK")
                } catch (t: Throwable) {
                    op.completed("Failed.")
                    result.put(t)
                } finally {
                    c.disconnect()
                }
            }

            result.take()?.let { if (it is Throwable) throw GradleException("Failed to download nodejs from $from", it) }
        }
    }

    private fun extract(file: File): File {
        val target = project.gradle.gradleUserHomeDir.resolve("nodejs")

        println("Extracting nodejs")
        project.tasks.create("extract-nodejs", Copy::class.java) { copy ->
            copy.from(project.tarTree(project.resources.gzip(file))).into(target)
        }.execute()

        val dir: File
        var name = file.name.substringBeforeLast('.', "")
        while (true) {
            if (target.resolve(name).isDirectory) {
                dir = target.resolve(name)
                break
            }
            name = name.substringBeforeLast('.', "")
            if (name.isEmpty()) throw GradleException("Failed to extract nodejs, check $target dir")
        }

        // note: we don't need to check .exe suffix as Windows doesn't require executable permission
        arrayOf("node")
                .map { dir.resolve("bin").resolve(it) }
                .filter { it.isFile && !it.canExecute() && !it.setExecutable(true, true) }
                .forEach { logger.warn("Failed to make $it executable") }

        val npmFile = dir.resolve("bin").resolve("npm") // TODO windows!
        if (npmFile.exists()) {
            npmFile.delete()
            Files.createSymbolicLink(npmFile.toPath(), dir.resolve("lib/node_modules/npm/bin/npm-cli.js").absoluteFile.toPath())
        }

        return dir
    }

    private fun newOperation() = services.get(ProgressLoggerFactory::class.java)!!.newOperation(javaClass)!!

    private fun ProgressLogger.downloadProgress(total: Long, bytesRead: Long) {
        if (total > 0L) {
            progress("${format(bytesRead)}/${format(total)} (${(bytesRead * 100L / total)}%)")
        } else {
            progress("${format(bytesRead)} downloaded")
        }
    }

    private val sizes = arrayOf("kb", "mb", "gb", "tb")
    private val BD1024 = BigDecimal.valueOf(1024)

    private fun format(size: Long): String {
        var current: BigDecimal = BigDecimal.valueOf(size)
        var cm = "b"

        for (m in sizes) {
            if (current > BD1024) {
                cm = m
                current = current.divide(BD1024)
            } else {
                break
            }
        }

        current = current.setScale(2, RoundingMode.FLOOR)
        return "${current.toPlainString()} $cm"
    }

    private fun mirror() = mirror.removeSuffix("/")
}
package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.internal.logging.progress.*
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

    @Internal
    private val target = project.gradle.gradleUserHomeDir.resolve("nodejs")

    @TaskAction
    fun doDownload() {
        val version = this.version.takeIf { it != "latest" } ?: detectLatest()
        val url = nodeUrl(version)

        expectedNodeJsDir(target, url.path.substringAfterLast('/'))?.let { executable(it) }?.let { existing ->
            if (askNodeJsVersion(existing).removePrefix("v") == version) {
                logger.info("nodejs $version found")
                nodePathTextFile.writeText(existing.parentFile.absolutePath)

                return
            }
        }

        val outFile = project.buildDir.resolve(url.path.substringAfterLast('/'))
        outFile.parentFile.mkdirsOrFail()

        download(url, outFile)
        val nodejsDir = extract(outFile)

        val executable = executable(nodejsDir) ?: throw GradleException("No node executable found in $nodejsDir")
        val actualVersion = askNodeJsVersion(executable)
        if (actualVersion.removePrefix("v") != version) {
            logger.warn("Downloaded version is $actualVersion")
        }

        nodePathTextFile.writeText(executable.parentFile.absolutePath)
    }

    private fun askNodeJsVersion(executable: File): String {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        project.exec { execute ->
            execute.executable(executable.absoluteFile)
            execute.args("--version").setStandardOutput(stdout).setErrorOutput(stderr)
        }

        return stdout.toByteArray().toString(Charsets.ISO_8859_1).trim()
    }

    private fun executable(nodejsDir: File) = arrayOf("node.exe", "bin/node").map { nodejsDir.resolve(it) }.firstOrNull { it.exists() }

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
        completed(op, "Got $version")

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

                    completed(op, "Completed.")
                    result.put("OK")
                } catch (t: Throwable) {
                    completed(op, "Failed.")
                    result.put(t)
                } finally {
                    c.disconnect()
                }
            }

            result.take()?.let { if (it is Throwable) throw GradleException("Failed to download nodejs from $from", it) }
        }
    }

    private fun extract(file: File): File {
        println("Extracting nodejs")
        project.copy { copy ->
            val tree = when {
                file.name.endsWith(".tar.gz") -> project.tarTree(project.resources.gzip(file))
                file.name.endsWith(".zip") -> project.zipTree(file)
                else -> throw GradleException("Unsupported package type $file")
            }

            copy.from(tree).into(target)
        }

        val dir = expectedNodeJsDir(target, file) ?: throw GradleException("Failed to extract nodejs, check $target dir")

        // note: we don't need to check .exe suffix as Windows doesn't require executable permission
        arrayOf("node")
                .map { dir.resolve("bin").resolve(it) }
                .filter { it.isFile && !it.canExecute() && !it.setExecutable(true, true) }
                .forEach { logger.warn("Failed to make $it executable") }

        val npmFile = dir.resolve("bin").resolve("npm")
        if (npmFile.exists()) {
            npmFile.delete()
            Files.createSymbolicLink(npmFile.toPath(), dir.resolve("lib/node_modules/npm/bin/npm-cli.js").absoluteFile.toPath())
        }

        return dir
    }

    private fun expectedNodeJsDir(target: File, bundleFileName: File): File? = expectedNodeJsDir(target, bundleFileName.nameWithoutExtension)

    private tailrec fun expectedNodeJsDir(target: File, name: String): File? = when {
        name.isEmpty() -> null
        target.resolve(name).isDirectory -> target.resolve(name)
        else -> expectedNodeJsDir(target, name.substringBeforeLast('.', ""))
    }

    private fun newOperation() = services.get(ProgressLoggerFactory::class.java)!!.newOperation(javaClass)!!

    private fun ProgressLogger.downloadProgress(total: Long, bytesRead: Long) {
        if (total > 0L) {
            progress("${format(bytesRead)}/${format(total)} (${(bytesRead * 100L / total)}%)")
        } else {
            progress("${format(bytesRead)} downloaded")
        }
    }

    private val completed by lazy<(ProgressLogger, String) -> Unit> {
        val all = logger.javaClass.methods.filter { it.name == "completed" }

        val singleParam = all.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (singleParam != null) {
            return@lazy { logger, message -> singleParam.invoke(logger, message) }
        }

        val messageWithFlag = all.firstOrNull { it.parameterCount == 2 && it.parameterTypes[0] == String::class.java }
        if (messageWithFlag != null) {
            return@lazy { logger, message -> messageWithFlag.invoke(logger, message, false) }
        }

        val withNoArgs = all.firstOrNull { it.parameterCount == 0 }
        if (withNoArgs != null) {
            return@lazy { logger, _ -> withNoArgs.invoke(logger) }
        }

        { _, _ -> Unit }
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

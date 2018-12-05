package org.jetbrains.kotlin.gradle.frontend

import org.jetbrains.kotlin.gradle.frontend.util.*
import org.junit.Test
import java.io.*
import kotlin.test.*

class OutputStreamBufferTest {
    private val actualOutput = ByteArrayOutputStream()
    private val buffered = OutputStreamWithBuffer(actualOutput, 10)

    @Test
    fun testSingleByteWrite() {
        buffered.write(1)
        buffered.write(2)
        buffered.write(3)

        assertTrue { byteArrayOf(1, 2, 3).contentEquals(buffered.lines()) }

        buffered.write(0x0d)
        buffered.write(15)
        buffered.write(16)
        buffered.write(17)
        buffered.write(18)
        buffered.write(19)
        buffered.write(20)

        assertTrue { byteArrayOf(1, 2, 3, 0x0d, 15, 16, 17, 18, 19, 20).contentEquals(buffered.lines()) }

        buffered.write(21)

        assertTrue { byteArrayOf(15, 16, 17, 18, 19, 20, 21).contentEquals(buffered.lines()) }

        assertTrue { byteArrayOf(1, 2, 3, 0x0d, 15, 16, 17, 18, 19, 20, 21).contentEquals(actualOutput.toByteArray()) }
    }

    @Test
    fun testBlockBytesWrite() {
        buffered.write(byteArrayOf(1, 2, 3))

        assertTrue { byteArrayOf(1, 2, 3).contentEquals(buffered.lines()) }

        buffered.write(byteArrayOf(0x0d, 15, 16, 17, 18, 19, 20))

        assertTrue { byteArrayOf(1, 2, 3, 0x0d, 15, 16, 17, 18, 19, 20).contentEquals(buffered.lines()) }

        buffered.write(byteArrayOf(21))

        assertTrue { byteArrayOf(15, 16, 17, 18, 19, 20, 21).contentEquals(buffered.lines()) }
        assertTrue { byteArrayOf(1, 2, 3, 0x0d, 15, 16, 17, 18, 19, 20, 21).contentEquals(actualOutput.toByteArray()) }
    }
}
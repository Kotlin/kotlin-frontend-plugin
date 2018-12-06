package sample

import kotlin.browser.*

actual class Sample {
    actual fun checkMe() = 12
}

actual object Platform {
    actual val name: String = "JS"
}

fun helloWorld(salutation: String) {
    val message = "$salutation from Kotlin.JS ${hello()}, check me value: ${Sample().checkMe()}"
    document.getElementById("js-response")?.textContent = message
}

fun main() {
    helloWorld("Hi!")
}
package aaa

import kotlin.js.*

external fun require(name: String): dynamic

@Suppress("unused")
fun ctor(d: dynamic, vararg args: dynamic): dynamic = js("""
var i = Object.create(d.prototype);
d.apply(i, args)
i
""")

val Vue: (dynamic) -> dynamic = { ctor(require("vue").default, it) }
val styles = require("styles.css")
val app = require("app.vue")

fun reset() {
    js("this").text = "kotlin"
}

fun main(args: Array<String>) {
    val v = Vue(json(
        "el" to "app",
        "render" to app.render,
        "data" to {json(
            "text" to "kotlin"
        )},
        "methods" to json(
            "reset" to js("reset")
        )
    ))
}



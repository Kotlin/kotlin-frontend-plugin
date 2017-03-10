package org.example.fullstack.server

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*

/**
 * @author Sergey Mashkov
 */
fun main(app: Application) {
    app.routing {
        get("/call") {
            call.respond("Hello, web!")
        }
    }
}

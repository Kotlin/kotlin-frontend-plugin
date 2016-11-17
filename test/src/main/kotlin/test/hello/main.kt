package test.hello

import kotlin.browser.*

fun main(args: Array<String>) {
    var application: ApplicationBase? = null

    val state: dynamic = module.hot?.let { hot ->
        hot.accept()

        hot.dispose { data ->
            data.appState = application?.dispose()
            application = null
        }

        hot.data
    }

    if (document.body != null) {
        application = start(state)
    } else {
        application = null
        document.addEventListener("DOMContentLoaded", { e -> application = start(state) })
    }

    println("ok...")
}

fun start(state: dynamic): ApplicationBase {
    val application = MainApplication()
    application.start(state?.appState ?: emptyMap())

    return application
}


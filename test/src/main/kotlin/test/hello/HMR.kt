package test.hello

@native val module: Module

@native interface Module {
    val hot: Hot?
}

@native interface Hot {
    val data: dynamic

    fun accept()
    fun accept(dependency: String, callback: () -> Unit)
    fun accept(dependencies: Array<String>, callback: (updated: Array<String>) -> Unit)

    fun dispose(callback: (data: dynamic) -> Unit)
}

@native
fun require(name: String): dynamic
package test.hello

import kotlin.browser.*

class MainApplication : ApplicationBase() {
    private lateinit var view: LinesView
    private lateinit var presenter: LinesPresenter

    override val stateKeys = listOf("lines")

    override fun start(state: Map<String, Any>) {
        view = LinesView(document.getElementById("lines")!!, document.getElementById("addForm")!!)
        presenter = LinesPresenter(view)

        state["lines"]?.let { linesState ->
            @Suppress("UNCHECKED_CAST")
            presenter.restore(linesState as Array<String>)
        }
    }

    override fun dispose() = mapOf(
            "lines" to presenter.dispose()
    )
}
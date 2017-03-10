package test.hello

import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.browser.*
import kotlin.dom.*

class WebLinesView(val linesHolder: Element, formRoot: Element) : LinesView {
    lateinit override var presenter: LinesPresenter

    @Suppress("UNCHECKED_CAST_TO_NATIVE_INTERFACE")
    private val input = formRoot.querySelector("input") as HTMLInputElement

    @Suppress("UNCHECKED_CAST_TO_NATIVE_INTERFACE")
    private val addButton = formRoot.querySelector("button") as HTMLButtonElement

    private val buttonHandler: (Event) -> Unit = {
        presenter.addButtonClicked()
    }

    private val inputHandler: (Event) -> Unit = { e ->
        if (e is KeyboardEvent && e.keyCode == 13) {
            presenter.inputEnterPressed()
        }
    }

    init {
        register()
    }

    override var inputText: String
        get() = input.value
        set(newValue) { input.value = newValue }

    override fun focusInput() {
        input.focus()
    }

    override fun addLine(lineText: String) {
        document.createElement("p").apply {
            textContent = " + " + lineText

            linesHolder.appendChild(this)
        }
    }

    override fun clearLines() {
        linesHolder.clear()
    }

    override fun dispose() {
        unregister()
    }

    private fun register() {
        addButton.addEventListener("click", buttonHandler)
        input.addEventListener("keypress", inputHandler)
    }

    private fun unregister() {
        addButton.removeEventListener("click", buttonHandler)
        input.removeEventListener("keypress", inputHandler)
    }
}

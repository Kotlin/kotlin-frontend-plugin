package test.hello

import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.browser.*
import kotlin.dom.*

class LinesView(val linesHolder: Element, val formRoot: Element) {
    lateinit var presenter: LinesPresenter
    private val input = formRoot.querySelector("input") as HTMLInputElement
    private val addButton = formRoot.querySelector("button") as HTMLButtonElement

    private val buttonHandler: (Event) -> Unit = { e ->
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

    var inputText: String
        get() = input.value
        set(newValue) { input.value = newValue }

    fun focusInput() {
        input.focus()
    }

    fun addLine(lineText: String) {
        document.createElement("p").apply {
            textContent = " -> " + lineText

            linesHolder.appendChild(this)
        }
    }

    fun clearLines() {
        linesHolder.clear()
    }

    fun dispose() {
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
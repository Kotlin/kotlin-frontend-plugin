package test.hello

class LinesPresenter(override val view: LinesView) : Presenter<LinesView, Array<String>> {
    init {
        view.presenter = this
    }

    private val lines = mutableListOf<String>()

    fun addButtonClicked() {
        val lineText = view.inputText

        lines.add(lineText)
        view.addLine(lineText)

        view.inputText = ""
        view.focusInput()
    }

    fun inputEnterPressed() {
        addButtonClicked()
    }

    override fun dispose(): Array<String> {
        view.dispose()
        return lines.toTypedArray()
    }

    override fun restore(state: Array<String>) {
        lines.addAll(state)

        view.clearLines()
        state.forEach { line ->
            view.addLine(line)
        }
    }
}
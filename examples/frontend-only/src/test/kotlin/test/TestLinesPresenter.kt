package test

import test.hello.*
import kotlin.test.*
import kotlin.test.Test

class TestLinesPresenter {
    @Test
    fun testSimpleAddViaEnterKey() {
        val view = MockLinesView()
        LinesPresenter(view)

        view.userEnterText("my line")
        view.userPressedEnter()

        // it is expected that the presenter will add line
        assertEquals(listOf("my line"), view.displayedLines)

        // focus is still on the text box
        assertTrue { view.textBoxFocused }
    }

    @Test
    fun testSimpleAddViaAddButton() {
        val view = MockLinesView()
        LinesPresenter(view)

        view.userEnterText("my line 2")
        view.userClickedAddButton()

        // it is expected that the presenter will add line
        assertEquals(listOf("my line 2"), view.displayedLines)

        // focus need to be returned back to the text box
        assertTrue { view.textBoxFocused }
    }

    @Test
    fun testSaveAndRestore() {
        val view1 = MockLinesView()
        val p1 = LinesPresenter(view1)

        view1.userEnterText("just a single line")
        view1.userPressedEnter()

        val state = p1.dispose()

        val view2 = MockLinesView()
        val p2 = LinesPresenter(view2)
        p2.restore(state)

        assertEquals(listOf("just a single line"), view2.displayedLines)
    }

    private class MockLinesView : LinesView {
        override lateinit var presenter: LinesPresenter
        var textBoxFocused = true
        val displayedLines = ArrayList<String>()

        fun userEnterText(text: String) {
            inputText += text
        }

        fun userPressedEnter() {
            presenter.inputEnterPressed()
        }

        fun userClickedAddButton() {
            textBoxFocused = false
            presenter.addButtonClicked()
        }

        override var inputText: String = ""

        override fun focusInput() {
            textBoxFocused = true
        }

        override fun addLine(lineText: String) {
            displayedLines += lineText
        }

        override fun clearLines() {
            displayedLines.clear()
        }

        override fun dispose() {
        }
    }
}

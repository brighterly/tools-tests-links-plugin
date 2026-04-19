package com.brighterly.testlinks.statusbar

import com.brighterly.testlinks.coverage.CloverCoverageService
import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.event.MouseAdapter
import javax.swing.JComponent

class CoverageStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val ID = "brighterly.testlinks.status"
    }

    override fun getId(): String = ID
    override fun getDisplayName(): String = "Tests Links coverage"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CoverageStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class CoverageStatusBarWidget(private val project: Project) :
    CustomStatusBarWidget {

    private val label = object : JBLabel() {
        override fun getText(): String = computeText()

        override fun getToolTipText(event: MouseEvent?): String = computeTooltip()
    }.apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = computeTooltip()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                showPopup(event)
            }
        })
    }

    private val component = Wrapper(label)

    override fun ID(): String = CoverageStatusBarWidgetFactory.ID

    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}
    override fun getComponent(): JComponent = component

    private fun computeText(): String {
        val sources = SourceIndexService.getInstance(project)
        val tests = TestIndexService.getInstance(project)
        val clover = CloverCoverageService.getInstance(project)
        val sourceNames = sources.allClassNames()
        val total = sourceNames.size
        if (total == 0) return "Tests ⚠"
        val covered = sourceNames.count { tests.findTestsFor(it).isNotEmpty() }
        val pct = (covered * 100) / total
        val cloverSuffix = if (clover.isLoaded()) " · clover ✓" else ""
        return "Tests $pct% ($covered/$total)$cloverSuffix"
    }

    private fun computeTooltip(): String =
        "Service + Controller test coverage — click for details"

    private fun showPopup(mouseEvent: MouseEvent) {
        label.text = computeText()
        label.toolTipText = computeTooltip()
        val sources = SourceIndexService.getInstance(project)
        val tests = TestIndexService.getInstance(project)
        val sourceNames = sources.allClassNames().sorted()
        val covered = sourceNames.count { tests.findTestsFor(it).isNotEmpty() }
        val uncovered = sourceNames.filter { tests.findTestsFor(it).isEmpty() }
        val totalMethods = tests.totalTestMethodCount()

        data class Item(
            val label: String,
            val action: () -> Unit = {},
            val selectable: Boolean = true,
        )

        val items = buildList {
            add(Item("Covered: $covered / ${sourceNames.size}", selectable = false))
            add(Item("Uncovered: ${uncovered.size}", selectable = false))
            add(Item("Total test methods: $totalMethods", selectable = false))
            add(Item("—", selectable = false))
            add(Item(label = "↻  Rebuild indexes", action = {
                sources.rebuildAsync()
                tests.rebuildAsync()
            }))
            if (uncovered.isNotEmpty()) {
                add(Item("—", selectable = false))
                add(Item("Uncovered classes:", selectable = false))
                uncovered.take(30).forEach { name ->
                    add(Item(label = "  · $name", action = { sources.openFirstClassFile(name) }))
                }
                if (uncovered.size > 30) {
                    add(Item("  … and ${uncovered.size - 30} more", selectable = false))
                }
            }
        }

        val step = object : BaseListPopupStep<Item>("Tests Coverage", items) {
            override fun getTextFor(value: Item): String = value.label
            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) selectedValue.action()
                return FINAL_CHOICE
            }
            override fun isSelectable(value: Item): Boolean = value.selectable
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showUnderneathOf(mouseEvent.component)
    }
}

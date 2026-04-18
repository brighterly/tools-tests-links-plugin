package com.brighterly.testlinks.statusbar

import com.brighterly.testlinks.coverage.CloverCoverageService
import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

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
    StatusBarWidget,
    StatusBarWidget.TextPresentation {

    override fun ID(): String = CoverageStatusBarWidgetFactory.ID

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getText(): String {
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

    override fun getTooltipText(): String =
        "Service + Controller test coverage — click for details"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { mouseEvent ->
        val sources = SourceIndexService.getInstance(project)
        val tests = TestIndexService.getInstance(project)
        val sourceNames = sources.allClassNames().sorted()
        val covered = sourceNames.count { tests.findTestsFor(it).isNotEmpty() }
        val uncovered = sourceNames.filter { tests.findTestsFor(it).isEmpty() }
        val totalMethods = tests.totalTestMethodCount()

        data class Item(val label: String, val action: () -> Unit = {})

        val items = buildList {
            add(Item("Covered: $covered / ${sourceNames.size}"))
            add(Item("Uncovered: ${uncovered.size}"))
            add(Item("Total test methods: $totalMethods"))
            add(Item("—"))
            add(Item("↻  Rebuild indexes") {
                sources.rebuildAsync()
                tests.rebuildAsync()
            })
            if (uncovered.isNotEmpty()) {
                add(Item("—"))
                add(Item("Uncovered classes:"))
                uncovered.take(30).forEach { name -> add(Item("  · $name")) }
                if (uncovered.size > 30) {
                    add(Item("  … and ${uncovered.size - 30} more"))
                }
            }
        }

        val step = object : BaseListPopupStep<Item>("Tests Coverage", items) {
            override fun getTextFor(value: Item): String = value.label
            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) selectedValue.action()
                return FINAL_CHOICE
            }
            override fun isSelectable(value: Item): Boolean = value.label != "—"
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showUnderneathOf(mouseEvent.component)
    }
}

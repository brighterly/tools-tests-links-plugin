package com.brighterly.testlinks.provider

import com.brighterly.testlinks.index.TestFileEntry
import com.brighterly.testlinks.run.TestRunner
import com.brighterly.testlinks.run.TestStateReader
import com.brighterly.testlinks.scaffold.CreateTestFileAction
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.awt.event.MouseEvent
import javax.swing.Icon

class TestFileLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val phpClass = element.parent as? PhpClass ?: return null
        if (phpClass.nameIdentifier !== element) return null
        if (phpClass.isAbstract || phpClass.isInterface || phpClass.isTrait) return null

        val className = phpClass.name.takeIf { it.isNotBlank() } ?: return null
        val virtualFile = phpClass.containingFile?.virtualFile ?: return null
        val filePath = virtualFile.path
        if (!isServiceOrController(filePath)) return null

        val project = phpClass.project
        val entries = TestIndexService.getInstance(project).findTestsFor(className)

        val stale = entries.isNotEmpty() && entries.any { virtualFile.modificationStamp > it.modStamp }
        val status = entries.firstOrNull()?.let { resolveStatus(project, it) } ?: TestStateReader.Status.UNKNOWN

        val icon = iconFor(entries, status, stale)
        val tooltip = tooltipFor(className, entries, status, stale)

        return TestsGutterLineMarkerInfo(
            element = element,
            range = element.textRange,
            markerIcon = icon,
            tooltipText = tooltip,
            entries = entries,
            project = project,
            phpClass = phpClass,
        )
    }

    /**
     * Custom LineMarkerInfo that overrides [createGutterRenderer] so the gutter
     * icon has both a left-click navigation handler and a right-click popup menu.
     */
    private class TestsGutterLineMarkerInfo(
        element: PsiElement,
        range: TextRange,
        private val markerIcon: Icon,
        private val tooltipText: String,
        private val entries: List<TestFileEntry>,
        private val project: Project,
        private val phpClass: PhpClass,
    ) : LineMarkerInfo<PsiElement>(
        element,
        range,
        markerIcon,
        { tooltipText },
        makeNavHandler(project, phpClass, entries),
        GutterIconRenderer.Alignment.RIGHT,
        { tooltipText },
    ) {
        override fun createGutterRenderer(): GutterIconRenderer? {
            return object : LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>(this@TestsGutterLineMarkerInfo) {
                override fun getClickAction(): AnAction = object : AnAction(), DumbAware {
                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = true
                        e.presentation.isVisible = true
                    }
                    override fun actionPerformed(e: AnActionEvent) {
                        val mouseEvent = e.inputEvent as? java.awt.event.MouseEvent
                        when {
                            entries.isEmpty() -> CreateTestFileAction.run(project, phpClass)
                            entries.size == 1 -> openTest(project, entries.single())
                            else -> if (mouseEvent != null) showPicker(project, entries, mouseEvent)
                        }
                    }
                }
                override fun getPopupMenuActions(): ActionGroup? = contextMenu(project, entries, phpClass)
            }
        }
    }

    companion object {
        fun makeNavHandler(
            project: Project,
            phpClass: PhpClass,
            entries: List<TestFileEntry>,
        ): GutterIconNavigationHandler<PsiElement> =
            GutterIconNavigationHandler { mouseEvent, _ ->
                when {
                    entries.isEmpty() -> CreateTestFileAction.run(project, phpClass)
                    entries.size == 1 -> openTest(project, entries.single())
                    else -> showPicker(project, entries, mouseEvent)
                }
            }

        private fun openTest(project: Project, entry: TestFileEntry) {
            OpenFileDescriptor(project, entry.file).navigate(true)
        }

        private fun showPicker(project: Project, entries: List<TestFileEntry>, mouseEvent: MouseEvent) {
            val step = object : BaseListPopupStep<TestFileEntry>("Open Test File", entries) {
                override fun getTextFor(value: TestFileEntry): String {
                    val rel = project.basePath?.let { base -> value.file.path.removePrefix("$base/") } ?: value.file.path
                    return "$rel  (${value.testMethodCount})"
                }

                override fun onChosen(selectedValue: TestFileEntry, finalChoice: Boolean): PopupStep<*>? {
                    openTest(project, selectedValue)
                    return FINAL_CHOICE
                }
            }
            JBPopupFactory.getInstance().createListPopup(step).show(RelativePoint(mouseEvent))
        }

        fun contextMenu(project: Project, entries: List<TestFileEntry>, phpClass: PhpClass): ActionGroup {
            val group = DefaultActionGroup()
            if (entries.isEmpty()) {
                group.add(simple("Create Test File", AllIcons.General.Add) {
                    CreateTestFileAction.run(project, phpClass)
                })
                return group
            }
            val primary = entries.first()
            group.add(simple("Open Test", AllIcons.Actions.EditSource) { openTest(project, primary) })
            group.addSeparator()
            group.add(simple("Run Test", AllIcons.Actions.Execute) {
                TestRunner.run(project, primary.file, TestRunner.Mode.RUN)
            })
            group.add(simple("Debug Test", AllIcons.Actions.StartDebugger) {
                TestRunner.run(project, primary.file, TestRunner.Mode.DEBUG)
            })
            group.add(simple("Run with Coverage", AllIcons.General.RunWithCoverage) {
                TestRunner.run(project, primary.file, TestRunner.Mode.RUN_WITH_COVERAGE)
            })
            return group
        }

        private fun simple(text: String, icon: Icon, fn: () -> Unit): AnAction =
            object : AnAction(text, null, icon), DumbAware {
                override fun actionPerformed(e: AnActionEvent) = fn()
            }
    }

    private fun isServiceOrController(path: String): Boolean =
        path.contains("/app/Services/") || path.contains("/app/Http/Controllers/")

    private fun resolveStatus(project: Project, entry: TestFileEntry): TestStateReader.Status {
        val psiFile = PsiManager.getInstance(project).findFile(entry.file) ?: return TestStateReader.Status.UNKNOWN
        val fqn = psiFile.children
            .filterIsInstance<com.jetbrains.php.lang.psi.elements.PhpNamespace>()
            .flatMap { it.children.filterIsInstance<PhpClass>() }
            .firstOrNull()?.fqn
            ?: psiFile.children.filterIsInstance<PhpClass>().firstOrNull()?.fqn
            ?: return TestStateReader.Status.UNKNOWN
        return TestStateReader.classStatus(project, fqn)
    }

    private fun iconFor(entries: List<TestFileEntry>, status: TestStateReader.Status, stale: Boolean): Icon {
        if (entries.isEmpty()) return AllIcons.General.Add
        if (entries.all { it.testMethodCount == 0 }) return AllIcons.General.BalloonWarning
        val base: Icon = when (status) {
            TestStateReader.Status.PASSED -> AllIcons.RunConfigurations.TestPassed
            TestStateReader.Status.FAILED -> AllIcons.RunConfigurations.TestFailed
            TestStateReader.Status.IGNORED -> AllIcons.RunConfigurations.TestIgnored
            TestStateReader.Status.UNKNOWN -> AllIcons.Gutter.ImplementedMethod
        }
        return if (stale) LayeredIcon.create(base, AllIcons.General.Warning) else base
    }

    private fun tooltipFor(
        className: String,
        entries: List<TestFileEntry>,
        status: TestStateReader.Status,
        stale: Boolean,
    ): String {
        if (entries.isEmpty()) {
            return "No test file for <b>$className</b> — click to create one"
        }
        val total = entries.sumOf { it.testMethodCount }
        val methodsWord = if (total == 1) "test" else "tests"
        val filesPart = if (entries.size == 1) entries.single().file.name else "${entries.size} files"
        val statusPart = when (status) {
            TestStateReader.Status.PASSED -> " · last run: passed"
            TestStateReader.Status.FAILED -> " · last run: failed"
            TestStateReader.Status.IGNORED -> " · last run: ignored"
            TestStateReader.Status.UNKNOWN -> ""
        }
        val stalePart = if (stale) "<br/><b>⚠ Subject modified after test was last updated</b>" else ""
        return "<b>$total $methodsWord</b> in $filesPart$statusPart$stalePart<br/><i>Right-click to Run / Debug</i>"
    }
}

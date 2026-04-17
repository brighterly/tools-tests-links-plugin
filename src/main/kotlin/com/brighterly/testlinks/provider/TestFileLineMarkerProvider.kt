package com.brighterly.testlinks.provider

import com.brighterly.testlinks.index.TestFileEntry
import com.brighterly.testlinks.scaffold.CreateTestFileAction
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.lang.psi.elements.PhpClass
import javax.swing.Icon

/**
 * Renders a gutter icon next to Service and Controller PHP class declarations.
 *
 * The icon indicates whether a PHPUnit test file exists for the class; clicking
 * it opens the test file (or a picker popup when multiple test files match).
 *
 * Performance: [getLineMarkerInfo] is called on the highlighter thread under a
 * read action. It performs only a path check + HashMap lookup; all file I/O and
 * regex counting happens off-thread in [TestIndexService].
 *
 * Anchoring: the marker is attached to the class *name identifier* (a leaf
 * element), following the IntelliJ guideline that prevents "multiple markers on
 * same line" warnings.
 */
class TestFileLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Anchor only on the identifier leaf of a PhpClass — not the whole class body.
        val phpClass = element.parent as? PhpClass ?: return null
        if (phpClass.nameIdentifier !== element) return null
        if (phpClass.isAbstract || phpClass.isInterface || phpClass.isTrait) return null

        val className = phpClass.name.takeIf { it.isNotBlank() } ?: return null
        val virtualFile = phpClass.containingFile?.virtualFile ?: return null
        val filePath = virtualFile.path

        if (!isServiceOrController(filePath)) return null

        val project = phpClass.project
        val entries = TestIndexService.getInstance(project).findTestsFor(className)

        val icon = iconFor(entries)
        val tooltip = tooltipFor(className, entries)

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            { mouseEvent, _ ->
                when {
                    entries.isEmpty() -> CreateTestFileAction.run(project, phpClass)
                    entries.size == 1 -> openTest(project, entries.single())
                    else -> showPicker(project, entries, mouseEvent)
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { tooltip },
        )
    }

    private fun isServiceOrController(path: String): Boolean {
        // Normalise to forward slashes; path() already uses them on mac/linux.
        return path.contains("/app/Services/") || path.contains("/app/Http/Controllers/")
    }

    private fun iconFor(entries: List<TestFileEntry>): Icon = when {
        entries.isEmpty() -> AllIcons.General.Add
        entries.all { it.testMethodCount == 0 } -> AllIcons.General.BalloonWarning
        else -> AllIcons.Gutter.ImplementedMethod
    }

    private fun tooltipFor(className: String, entries: List<TestFileEntry>): String {
        if (entries.isEmpty()) {
            return "No test file for <b>$className</b> — click to create one"
        }
        val total = entries.sumOf { it.testMethodCount }
        val methodsWord = if (total == 1) "test" else "tests"
        val filesPart = if (entries.size == 1) {
            entries.single().file.name
        } else {
            "${entries.size} files"
        }
        return "<b>$total $methodsWord</b> in $filesPart — click to open"
    }

    private fun openTest(project: com.intellij.openapi.project.Project, entry: TestFileEntry) {
        OpenFileDescriptor(project, entry.file).navigate(true)
    }

    private fun showPicker(
        project: com.intellij.openapi.project.Project,
        entries: List<TestFileEntry>,
        mouseEvent: java.awt.event.MouseEvent,
    ) {
        val step = object : BaseListPopupStep<TestFileEntry>(
            "Open Test File",
            entries,
        ) {
            override fun getTextFor(value: TestFileEntry): String {
                val rel = project.basePath
                    ?.let { base -> value.file.path.removePrefix("$base/") }
                    ?: value.file.path
                return "$rel  (${value.testMethodCount})"
            }

            override fun onChosen(selectedValue: TestFileEntry, finalChoice: Boolean): PopupStep<*>? {
                openTest(project, selectedValue)
                return FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance()
            .createListPopup(step)
            .show(RelativePoint(mouseEvent))
    }
}

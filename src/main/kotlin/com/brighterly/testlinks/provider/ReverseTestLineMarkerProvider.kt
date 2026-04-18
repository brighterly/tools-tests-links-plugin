package com.brighterly.testlinks.provider

import com.brighterly.testlinks.service.TestIndexService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Reverse navigation: gutter icon on test class declarations that jumps to the
 * subject Service/Controller class.
 *
 * Only activates when:
 *   - the file is inside the project's test roots (per [TestIndexService.isInTestRoots])
 *   - the class name ends with "Test"
 *   - a PHP class matching the stripped name exists under app/Services or app/Http/Controllers
 *
 * Uses PhpIndex to resolve the subject by short name; no extra index is needed.
 */
class ReverseTestLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val phpClass = element.parent as? PhpClass ?: return null
        if (phpClass.nameIdentifier !== element) return null

        val className = phpClass.name
        if (!className.endsWith("Test") || className.length <= 4) return null

        val virtualFile = phpClass.containingFile?.virtualFile ?: return null
        val project = phpClass.project
        val service = TestIndexService.getInstance(project)
        if (!service.isInTestRoots(virtualFile)) return null

        val subjectName = className.removeSuffix("Test")
        val subjects = PhpIndex.getInstance(project)
            .getClassesByName(subjectName)
            .filter { candidate ->
                val path = candidate.containingFile?.virtualFile?.path ?: return@filter false
                (path.contains("/app/Services/") || path.contains("/app/Http/Controllers/")) &&
                    !candidate.isAbstract && !candidate.isInterface && !candidate.isTrait
            }

        if (subjects.isEmpty()) return null

        val tooltip = if (subjects.size == 1) {
            "Go to <b>$subjectName</b>"
        } else {
            "Go to <b>$subjectName</b> — ${subjects.size} candidates"
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Gutter.OverridingMethod,
            { tooltip },
            { mouseEvent, _ ->
                when (subjects.size) {
                    1 -> navigate(subjects.single())
                    else -> showPicker(project, subjects, mouseEvent)
                }
            },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }

    private fun navigate(phpClass: PhpClass) {
        val file = phpClass.containingFile?.virtualFile ?: return
        val offset = ReadAction.compute<Int, Throwable> { phpClass.textOffset }
        OpenFileDescriptor(phpClass.project, file, offset).navigate(true)
    }

    private fun showPicker(
        project: com.intellij.openapi.project.Project,
        candidates: List<PhpClass>,
        mouseEvent: java.awt.event.MouseEvent,
    ) {
        val step = object : BaseListPopupStep<PhpClass>("Go to Subject Class", candidates) {
            override fun getTextFor(value: PhpClass): String =
                ReadAction.compute<String, Throwable> {
                    val rel = project.basePath
                        ?.let { base -> value.containingFile?.virtualFile?.path?.removePrefix("$base/") }
                        ?: value.fqn
                    rel ?: value.fqn
                }

            override fun onChosen(selectedValue: PhpClass, finalChoice: Boolean): PopupStep<*>? {
                navigate(selectedValue)
                return FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance()
            .createListPopup(step)
            .show(RelativePoint(mouseEvent))
    }
}

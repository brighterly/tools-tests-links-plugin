package com.brighterly.testlinks.action

import com.brighterly.testlinks.scaffold.CreateTestFileAction
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Ctrl+Alt+Shift+T: open the test for the class under caret. If no test
 * exists, offer to scaffold one. This complements PhpStorm's built-in
 * "Go to Test" (Ctrl+Shift+T) with our index-aware lookup and scaffolding.
 */
class GoToTestAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return

        var current: com.intellij.psi.PsiElement? = element
        while (current != null && current !is PhpClass) current = current.parent
        val phpClass = current as? PhpClass ?: return

        val name = phpClass.name.ifBlank { return }
        val entries = TestIndexService.getInstance(project).findTestsFor(name)

        when (entries.size) {
            0 -> CreateTestFileAction.run(project, phpClass)
            1 -> OpenFileDescriptor(project, entries.single().file).navigate(true)
            else -> {
                val step = object : BaseListPopupStep<com.brighterly.testlinks.index.TestFileEntry>(
                    "Open Test File", entries,
                ) {
                    override fun getTextFor(value: com.brighterly.testlinks.index.TestFileEntry): String {
                        val rel = project.basePath?.let { base -> value.file.path.removePrefix("$base/") } ?: value.file.path
                        return "$rel  (${value.testMethodCount})"
                    }
                    override fun onChosen(selectedValue: com.brighterly.testlinks.index.TestFileEntry, finalChoice: Boolean): PopupStep<*>? {
                        OpenFileDescriptor(project, selectedValue.file).navigate(true)
                        return FINAL_CHOICE
                    }
                }
                JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor)
            }
        }
    }
}

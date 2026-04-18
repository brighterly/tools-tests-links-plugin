package com.brighterly.testlinks.intention

import com.brighterly.testlinks.scaffold.CreateTestFileAction
import com.brighterly.testlinks.scaffold.TestFileScaffolder
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Alt+Enter → "Create test file for …" when the caret sits on a Service or
 * Controller class whose test file doesn't yet exist.
 */
class CreateTestIntention : IntentionAction {

    override fun getText(): String = "Create test file for this class"
    override fun getFamilyName(): String = "Tests Links"
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val phpClass = classAt(project, editor, file) ?: return false
        if (phpClass.isAbstract || phpClass.isInterface || phpClass.isTrait) return false
        val path = phpClass.containingFile?.virtualFile?.path ?: return false
        if (!path.contains("/app/Services/") && !path.contains("/app/Http/Controllers/")) return false
        val name = phpClass.name.ifBlank { return false }
        val basePath = project.basePath ?: return false
        if (TestFileScaffolder.planFor(phpClass, basePath) == null) return false
        return TestIndexService.getInstance(project).findTestsFor(name).isEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val phpClass = classAt(project, editor, file) ?: return
        CreateTestFileAction.run(project, phpClass)
    }

    private fun classAt(project: Project, editor: Editor?, file: PsiFile?): PhpClass? {
        editor ?: return null
        file ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            if (current is PhpClass) return current
            current = current.parent
        }
        return null
    }
}

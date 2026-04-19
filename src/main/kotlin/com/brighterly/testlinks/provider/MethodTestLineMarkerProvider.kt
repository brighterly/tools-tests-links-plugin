package com.brighterly.testlinks.provider

import com.brighterly.testlinks.run.TestStateReader
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier

/**
 * Gutter markers on each public method in a Service / Controller that has a
 * corresponding `test{MethodName}` in one of the matched test files.
 *
 * Detection strategy: regex over the test file text for
 * `function test<PascalCaseName>\b`. Fast, avoids PSI for the test file
 * (it may not even be in the PSI cache).
 */
class MethodTestLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val method = element.parent as? Method ?: return null
        if (method.nameIdentifier !== element) return null
        if (method.access != PhpModifier.Access.PUBLIC) return null
        if (method.name.isBlank() || method.name.startsWith("__")) return null

        val containingClass = method.containingClass ?: return null
        if (containingClass.isAbstract || containingClass.isInterface || containingClass.isTrait) return null

        val filePath = containingClass.containingFile?.virtualFile?.path ?: return null
        if (!filePath.contains("/app/Services/") && !filePath.contains("/app/Http/Controllers/")) return null

        val className = containingClass.name.ifBlank { return null }
        val project = method.project
        val tests = TestIndexService.getInstance(project).findTestsFor(className)
        if (tests.isEmpty()) return null

        val expectedTestName = "test" + method.name.replaceFirstChar { it.uppercase() }
        val matches = tests.filter { entry ->
            val text = runCatching { String(entry.file.contentsToByteArray(), entry.file.charset) }.getOrNull()
                ?: return@filter false
            Regex("""\bfunction\s+${Regex.escape(expectedTestName)}\s*\(""").containsMatchIn(text)
        }
        if (matches.isEmpty()) return null

        val status = matches.firstNotNullOfOrNull { entry ->
            resolveClassFqn(project, entry.file)?.let { fqn ->
                TestStateReader.methodStatus(project, fqn, expectedTestName)
            }
        } ?: TestStateReader.Status.UNKNOWN

        val icon = when (status) {
            TestStateReader.Status.PASSED -> AllIcons.RunConfigurations.TestPassed
            TestStateReader.Status.FAILED -> AllIcons.RunConfigurations.TestFailed
            TestStateReader.Status.IGNORED -> AllIcons.RunConfigurations.TestIgnored
            TestStateReader.Status.UNKNOWN -> AllIcons.Gutter.ImplementedMethod
        }

        val tooltip = "Tested by <b>$expectedTestName()</b> — click to open"

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            { _, _ -> navigateToTestMethod(project, matches.first().file, expectedTestName) },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }

    private fun navigateToTestMethod(project: Project, file: com.intellij.openapi.vfs.VirtualFile, methodName: String) {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        val testClass = psiFile?.children
            ?.filterIsInstance<com.jetbrains.php.lang.psi.elements.PhpNamespace>()
            ?.flatMap { it.children.filterIsInstance<PhpClass>() }
            ?.firstOrNull()
            ?: psiFile?.children?.filterIsInstance<PhpClass>()?.firstOrNull()
        val offset = testClass?.ownMethods?.firstOrNull { it.name == methodName }?.textOffset ?: 0
        OpenFileDescriptor(project, file, offset).navigate(true)
    }

    private fun resolveClassFqn(project: Project, file: com.intellij.openapi.vfs.VirtualFile): String? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        return psiFile.children
            .filterIsInstance<com.jetbrains.php.lang.psi.elements.PhpNamespace>()
            .flatMap { it.children.filterIsInstance<PhpClass>() }
            .firstOrNull()?.fqn
            ?: psiFile.children.filterIsInstance<PhpClass>().firstOrNull()?.fqn
    }
}

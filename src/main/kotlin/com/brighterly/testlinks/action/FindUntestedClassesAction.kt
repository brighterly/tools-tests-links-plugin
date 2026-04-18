package com.brighterly.testlinks.action

import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.io.File

/**
 * Tools → Find Untested Services & Controllers.
 *
 * Cross-references the source index against the test index, lists every
 * Service/Controller class name without a test file, and lets the user pick
 * one to jump directly to its source file.
 */
class FindUntestedClassesAction : AnAction("Find Untested Services & Controllers") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tests = TestIndexService.getInstance(project)
        val sources = SourceIndexService.getInstance(project)

        val uncovered = sources.allClassNames().sorted().filter { tests.findTestsFor(it).isEmpty() }
        if (uncovered.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("Everything covered 🎉")
                .showInFocusCenter()
            return
        }

        val fileByName = buildFileMap(project, uncovered.toSet())

        val step = object : BaseListPopupStep<String>("Untested (${uncovered.size})", uncovered) {
            override fun getTextFor(value: String): String {
                val rel = fileByName[value]
                    ?.let { vf -> project.basePath?.let { base -> vf.path.removePrefix("$base/") } ?: vf.path }
                    ?: value
                return "$value  —  $rel"
            }
            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                fileByName[selectedValue]?.let { OpenFileDescriptor(project, it).navigate(true) }
                return FINAL_CHOICE
            }
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val popup = JBPopupFactory.getInstance().createListPopup(step)
        if (editor != null) popup.showInBestPositionFor(editor) else popup.showInFocusCenter()
    }

    private fun buildFileMap(project: com.intellij.openapi.project.Project, names: Set<String>): Map<String, VirtualFile> {
        val basePath = project.basePath ?: return emptyMap()
        val fs = LocalFileSystem.getInstance()
        val roots = listOfNotNull(
            fs.findFileByIoFile(File(basePath, "app/Services")),
            fs.findFileByIoFile(File(basePath, "app/Http/Controllers")),
        )
        val result = mutableMapOf<String, VirtualFile>()
        for (root in roots) {
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && file.name.endsWith(".php")) {
                        val name = file.name.removeSuffix(".php")
                        if (name in names && name !in result) result[name] = file
                    }
                    return true
                }
            })
        }
        return result
    }
}

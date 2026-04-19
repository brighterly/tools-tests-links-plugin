package com.brighterly.testlinks.scaffold

import com.brighterly.testlinks.service.TestIndexService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.io.File

/**
 * Scaffolds a new PHPUnit test file for the given Service/Controller class,
 * opens it in the editor, and refreshes the test index.
 *
 * Invoked from the gutter icon when no existing test file is found.
 */
object CreateTestFileAction {

    fun run(project: Project, phpClass: PhpClass) {
        val basePath = project.basePath ?: return
        val plan = ReadAction.nonBlocking<TestFileScaffolder.Plan?> {
            TestFileScaffolder.planFor(phpClass, basePath)
        }
            .expireWith(project)
            .inSmartMode(project)
            .executeSynchronously() ?: run {
            notify(
                project,
                "Cannot create test: class is not under app/Services or app/Http/Controllers.",
                NotificationType.WARNING,
            )
            return
        }

        val absolutePath = "$basePath/${plan.relativeTestPath}"
        val existing = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath)
        if (existing != null) {
            // File snuck in between index scans — just open it.
            TestIndexService.getInstance(project).refreshFile(existing)
            OpenFileDescriptor(project, existing).navigate(true)
            return
        }

        val confirmed = Messages.showYesNoDialog(
            project,
            "Create test file at:\n${plan.relativeTestPath}",
            "Create Test File",
            Messages.getQuestionIcon(),
        ) == Messages.YES

        if (!confirmed) return

        val created = createFile(project, basePath, plan)
        if (created == null) {
            notify(project, "Failed to create ${plan.relativeTestPath}", NotificationType.ERROR)
            return
        }

        TestIndexService.getInstance(project).refreshFile(created)
        OpenFileDescriptor(project, created).navigate(true)
    }

    private fun createFile(
        project: Project,
        basePath: String,
        plan: TestFileScaffolder.Plan,
    ): VirtualFile? {
        val parentDirPath = "$basePath/${plan.relativeTestPath.substringBeforeLast('/')}"
        // Create intermediate directories outside the write command — Files API handles
        // it, and we still need the VirtualFile to exist before the write command.
        val parentIoDir = File(parentDirPath)
        val parentVf: VirtualFile = WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            parentIoDir.mkdirs()
            VfsUtil.createDirectoryIfMissing(parentDirPath)
                ?: LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentIoDir)
                ?: error("Unable to create test directory: $parentDirPath")
        }

        val fileName = plan.relativeTestPath.substringAfterLast('/')

        return WriteCommandAction.writeCommandAction(project)
            .withName("Create Test File")
            .compute<VirtualFile?, RuntimeException> {
                val file = parentVf.createChildData(this, fileName)
                VfsUtil.saveText(file, plan.content)
                file
            }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tests Links")
            .createNotification(message, type)
            .notify(project)
    }
}

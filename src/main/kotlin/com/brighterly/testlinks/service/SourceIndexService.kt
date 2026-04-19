package com.brighterly.testlinks.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Project-scoped index of Service / Controller class names (by filename),
 * used to compute coverage statistics in the status bar widget.
 *
 * We deliberately index by *filename without .php extension* instead of parsing
 * PSI — avoids PhpIndex dependencies during startup and keeps scan cost at
 * O(files) with no allocation per PSI element.
 *
 * This is an approximation: abstract classes, interfaces, traits, and files
 * containing multiple classes are treated the same as a regular class. For a
 * coverage counter in the status bar this is fine.
 */
@Service(Service.Level.PROJECT)
class SourceIndexService(private val project: Project) {

    // Simple class names: "FooService", "BarController"
    private val classes = CopyOnWriteArraySet<String>()
    private val filesByClassName = ConcurrentHashMap<String, CopyOnWriteArraySet<VirtualFile>>()

    fun allClassNames(): Set<String> = classes.toSet()
    fun findFilesForClassName(className: String): List<VirtualFile> = filesByClassName[className]?.toList() ?: emptyList()

    fun size(): Int = classes.size

    fun rebuildAsync() {
        ReadAction.nonBlocking<Unit> { rebuildBlocking() }
            .inSmartMode(project)
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun rebuildBlocking() {
        val basePath = project.basePath ?: return
        val fs = LocalFileSystem.getInstance()
        val roots = listOfNotNull(
            fs.findFileByIoFile(File(basePath, "app/Services")),
            fs.findFileByIoFile(File(basePath, "app/Http/Controllers")),
        )

        val collected = mutableSetOf<String>()
        for (root in roots) {
            VfsUtilCore.visitChildrenRecursively(
                root,
                object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && file.name.endsWith(".php")) {
                            val name = file.name.removeSuffix(".php")
                            // Skip obvious non-class files like plain DTOs aren't skipped;
                            // filename alone can't tell us. Include everything.
                            collected += name
                        }
                        return true
                    }
                },
            )
        }

        classes.clear()
        filesByClassName.clear()
        classes.addAll(collected)
        collected.forEach { name ->
            filesByClassName.computeIfAbsent(name) { CopyOnWriteArraySet() }
        }
        for (root in roots) {
            VfsUtilCore.visitChildrenRecursively(
                root,
                object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && file.name.endsWith(".php")) {
                            val name = file.name.removeSuffix(".php")
                            filesByClassName.computeIfAbsent(name) { CopyOnWriteArraySet() }.add(file)
                        }
                        return true
                    }
                },
            )
        }
    }

    fun openFirstClassFile(className: String): Boolean {
        val target = findFilesForClassName(className).sortedBy { it.path }.firstOrNull() ?: return false
        OpenFileDescriptor(project, target).navigate(true)
        return true
    }

    companion object {
        fun getInstance(project: Project): SourceIndexService = project.service()
    }
}

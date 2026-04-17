package com.brighterly.testlinks.service

import com.brighterly.testlinks.config.PhpUnitConfigReader
import com.brighterly.testlinks.index.TestFileEntry
import com.brighterly.testlinks.index.TestMethodCounter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped index of PHPUnit test files keyed by the class name they test
 * (i.e. filename without the `Test.php` suffix).
 *
 * Built once at project open off the EDT, then incrementally refreshed via VFS events.
 * Gutter rendering reads [findTestsFor] with O(1) cost and no disk I/O.
 */
@Service(Service.Level.PROJECT)
class TestIndexService(private val project: Project) {

    private val logger = thisLogger()

    // className (e.g. "AutoActivationService") -> matching test files
    private val index = ConcurrentHashMap<String, CopyOnWriteArrayList<TestFileEntry>>()

    // Cached testsuite roots for quick path containment checks.
    @Volatile
    private var testRoots: List<VirtualFile> = emptyList()

    fun findTestsFor(className: String): List<TestFileEntry> =
        index[className]?.toList() ?: emptyList()

    /** Total distinct class names with at least one test file. */
    fun indexedClassCount(): Int = index.size

    /** Total test methods across all indexed test files. */
    fun totalTestMethodCount(): Int =
        index.values.sumOf { list -> list.sumOf { it.testMethodCount } }

    fun getTestRoots(): List<VirtualFile> = testRoots

    /**
     * Returns true if the given virtual file lives under one of the configured testsuite roots.
     */
    fun isInTestRoots(file: VirtualFile): Boolean =
        testRoots.any { root -> VfsUtilCore.isAncestor(root, file, false) }

    /**
     * Full async rebuild — scans every *Test.php under each testsuite root and
     * computes method counts. Triggers editor refresh when done.
     */
    fun rebuildAsync() {
        ReadAction.nonBlocking<Unit> { rebuildBlocking() }
            .inSmartMode(project)
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun rebuildBlocking() {
        val roots = PhpUnitConfigReader.resolveTestRoots(project)
        testRoots = roots
        index.clear()

        for (root in roots) {
            VfsUtilCore.visitChildrenRecursively(
                root,
                object : com.intellij.openapi.vfs.VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && isTestFile(file)) {
                            addToIndex(file)
                        }
                        return true
                    }
                },
            )
        }

        logger.info("TestIndex built: ${index.size} classes, ${testRoots.size} roots")
        scheduleEditorRefresh()
    }

    /**
     * Refresh a single file in the index. Called from VFS listener.
     * Removes any stale entries for the file first, then re-adds if still a test file.
     */
    fun refreshFile(file: VirtualFile) {
        removeFromIndex(file)
        if (file.exists() && isTestFile(file) && isInTestRoots(file)) {
            addToIndex(file)
        }
        scheduleEditorRefresh()
    }

    /**
     * Remove a file from every key's entry list. Used on delete or rename.
     */
    fun removeFromIndex(file: VirtualFile) {
        val iterator = index.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.removeAll { it.file.path == file.path }
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun addToIndex(file: VirtualFile) {
        val className = classNameFromTestFile(file) ?: return
        val text = runCatching { String(file.contentsToByteArray(), file.charset) }.getOrNull() ?: return
        val entry = TestFileEntry(
            file = file,
            modStamp = file.modificationStamp,
            testMethodCount = TestMethodCounter.count(text),
        )
        index.computeIfAbsent(className) { CopyOnWriteArrayList() }.add(entry)
    }

    private fun isTestFile(file: VirtualFile): Boolean =
        file.name.endsWith("Test.php")

    private fun classNameFromTestFile(file: VirtualFile): String? {
        val name = file.name
        if (!name.endsWith("Test.php")) return null
        return name.removeSuffix("Test.php").takeIf { it.isNotBlank() }
    }

    private fun scheduleEditorRefresh() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                @Suppress("DEPRECATION")
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }

    companion object {
        fun getInstance(project: Project): TestIndexService = project.service()
    }
}

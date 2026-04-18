package com.brighterly.testlinks.listener

import com.brighterly.testlinks.coverage.CloverCoverageService
import com.brighterly.testlinks.routes.RouteIndexService
import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Keeps [TestIndexService] in sync with the filesystem.
 *
 * - *Test.php created / modified / moved-into-testsuite  → refresh that file
 * - *Test.php deleted / moved-out                        → remove from index
 * - phpunit.xml or phpunit.xml.dist changed              → full rebuild
 *
 * Listener runs on every open project. All index operations are cheap
 * (HashMap updates; regex only when a single file is re-read).
 */
class TestFileVfsListener : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        if (events.isEmpty()) return

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val testService = TestIndexService.getInstance(project)
            val sourceService = SourceIndexService.getInstance(project)
            val routeService = RouteIndexService.getInstance(project)
            val cloverService = CloverCoverageService.getInstance(project)
            var needsFullRebuild = false
            var sourcesChanged = false
            var routesChanged = false
            var coverageChanged = false

            for (event in events) {
                val file = event.file ?: continue

                if (isPhpUnitConfig(file)) {
                    needsFullRebuild = true
                    continue
                }

                if (isRouteFile(file)) {
                    routesChanged = true
                    continue
                }

                if (isCoverageFile(file)) {
                    coverageChanged = true
                    continue
                }

                if (isSourceFile(file)) {
                    sourcesChanged = true
                    continue
                }

                if (!isPhpTestFile(file)) continue

                when (event) {
                    is VFileDeleteEvent -> testService.removeFromIndex(file)
                    is VFileCreateEvent,
                    is VFileContentChangeEvent,
                    is VFileCopyEvent,
                    is VFileMoveEvent,
                    is VFilePropertyChangeEvent -> testService.refreshFile(file)
                }
            }

            if (needsFullRebuild) {
                testService.rebuildAsync()
                sourceService.rebuildAsync()
            } else if (sourcesChanged) {
                sourceService.rebuildAsync()
            }
            if (routesChanged) routeService.rebuildAsync()
            if (coverageChanged) cloverService.refresh()
        }
    }

    private fun isPhpTestFile(file: VirtualFile): Boolean =
        !file.isDirectory && file.name.endsWith("Test.php")

    private fun isPhpUnitConfig(file: VirtualFile): Boolean =
        !file.isDirectory && (file.name == "phpunit.xml" || file.name == "phpunit.xml.dist")

    private fun isSourceFile(file: VirtualFile): Boolean {
        if (file.isDirectory || !file.name.endsWith(".php")) return false
        val path = file.path
        return path.contains("/app/Services/") || path.contains("/app/Http/Controllers/")
    }

    private fun isRouteFile(file: VirtualFile): Boolean =
        !file.isDirectory && file.name.endsWith(".php") && file.path.contains("/routes/")

    private fun isCoverageFile(file: VirtualFile): Boolean =
        !file.isDirectory && file.name == "clover.xml"
}

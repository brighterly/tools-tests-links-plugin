package com.brighterly.testlinks.listener

import com.brighterly.testlinks.coverage.CloverCoverageService
import com.brighterly.testlinks.routes.RouteIndexService
import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Kicks off initial test, source, route, and coverage index scans when the
 * project opens. All work is async on the application executor.
 */
class TestIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        TestIndexService.getInstance(project).rebuildAsync()
        SourceIndexService.getInstance(project).rebuildAsync()
        RouteIndexService.getInstance(project).rebuildAsync()
        CloverCoverageService.getInstance(project).refresh()
    }
}

package com.brighterly.testlinks.listener

import com.brighterly.testlinks.service.SourceIndexService
import com.brighterly.testlinks.service.TestIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Kicks off initial test and source index scans when the project opens.
 * Runs async on the application executor; never blocks the EDT.
 */
class TestIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        TestIndexService.getInstance(project).rebuildAsync()
        SourceIndexService.getInstance(project).rebuildAsync()
    }
}

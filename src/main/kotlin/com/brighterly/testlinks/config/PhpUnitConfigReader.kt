package com.brighterly.testlinks.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Resolves test root directories to scan.
 *
 * Uses `{projectRoot}/tests/` — the universal Laravel / PHPUnit convention.
 * Test files named `*Test.php` anywhere under this tree are indexed.
 *
 * We intentionally do NOT read phpunit.xml <testsuites>: projects often keep
 * test files in directories outside the declared testsuites (e.g. a custom
 * `tests/Services/` structure), and we care about file discovery by naming
 * convention rather than PHPUnit's execution configuration.
 */
object PhpUnitConfigReader {

    fun resolveTestRoots(project: Project): List<VirtualFile> {
        val basePath = project.basePath ?: return emptyList()
        val testsDir = File(basePath, "tests")
        val vf = LocalFileSystem.getInstance().findFileByIoFile(testsDir) ?: return emptyList()
        return if (vf.isDirectory) listOf(vf) else emptyList()
    }
}

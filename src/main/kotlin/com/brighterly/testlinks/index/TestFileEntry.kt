package com.brighterly.testlinks.index

import com.intellij.openapi.vfs.VirtualFile

/**
 * One discovered PHPUnit test file. Cached per-file in TestIndexService.
 *
 * @property file the test file
 * @property modStamp VirtualFile.modificationStamp captured when [testMethodCount] was computed,
 *                    used to decide whether the count needs to be refreshed.
 * @property testMethodCount number of test methods detected via regex
 *                            (function test*, #[Test], @test docblock tag).
 */
data class TestFileEntry(
    val file: VirtualFile,
    val modStamp: Long,
    val testMethodCount: Int,
)

package com.brighterly.testlinks.routes

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Indexes PHP files under `routes/` for `Route::{verb}('/uri', [FooController::class, 'method'])`
 * declarations, keyed by "FooController::method" for fast lookup during
 * controller-test scaffolding.
 *
 * The regex handles the most common Laravel route syntaxes:
 *
 *   Route::get('/foo', [FooController::class, 'index']);
 *   Route::post('/bar', FooController::class);
 *   Route::apiResource('/users', UserController::class);
 *
 * It does NOT follow groups, prefixes, or `->name(...)` chains — the URI
 * inside the call is what we capture. That covers ~90% of real endpoints.
 */
@Service(Service.Level.PROJECT)
class RouteIndexService(private val project: Project) {

    private val logger = thisLogger()

    // key "ControllerName::method" → URI
    private val index = ConcurrentHashMap<String, String>()

    fun findUriFor(controllerShortName: String, method: String = "index"): String? {
        val exact = index["$controllerShortName::$method"]
        if (exact != null) return exact
        // Fallback: any route referencing this controller.
        val anyKey = index.keys.firstOrNull { it.startsWith("$controllerShortName::") }
        return anyKey?.let { index[it] }
    }

    fun rebuildAsync() {
        ReadAction.nonBlocking<Unit> { rebuildBlocking() }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun rebuildBlocking() {
        val basePath = project.basePath ?: return
        val routesDir = LocalFileSystem.getInstance().findFileByIoFile(File(basePath, "routes"))
            ?: return

        val fresh = mutableMapOf<String, String>()
        VfsUtilCore.visitChildrenRecursively(routesDir, object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory && file.name.endsWith(".php")) {
                    try {
                        val text = String(file.contentsToByteArray(), file.charset)
                        collectRoutes(text, fresh)
                    } catch (_: Exception) { /* ignore */ }
                }
                return true
            }
        })
        index.clear()
        index.putAll(fresh)
        logger.info("RouteIndex rebuilt: ${index.size} route→controller entries")
    }

    private fun collectRoutes(text: String, out: MutableMap<String, String>) {
        // Route::get('/uri', [FooController::class, 'method'])
        val arrayForm = Regex(
            """Route::(\w+)\(\s*['"]([^'"]+)['"]\s*,\s*\[\s*([A-Za-z_][A-Za-z0-9_]*)::class\s*,\s*['"](\w+)['"]\s*]""",
        )
        for (m in arrayForm.findAll(text)) {
            val uri = m.groupValues[2]
            val controller = m.groupValues[3]
            val method = m.groupValues[4]
            out["$controller::$method"] = normalize(uri)
        }

        // Route::get('/uri', FooController::class) — single-action controller
        val invokeForm = Regex(
            """Route::(\w+)\(\s*['"]([^'"]+)['"]\s*,\s*([A-Za-z_][A-Za-z0-9_]*)::class\s*\)""",
        )
        for (m in invokeForm.findAll(text)) {
            val uri = m.groupValues[2]
            val controller = m.groupValues[3]
            out["$controller::__invoke"] = normalize(uri)
        }

        // Route::apiResource('/uri', FooController::class)
        val resourceForm = Regex(
            """Route::(apiResource|resource)\(\s*['"]([^'"]+)['"]\s*,\s*([A-Za-z_][A-Za-z0-9_]*)::class""",
        )
        for (m in resourceForm.findAll(text)) {
            val uri = m.groupValues[2]
            val controller = m.groupValues[3]
            out["$controller::index"] = normalize(uri)
        }
    }

    private fun normalize(uri: String): String =
        if (uri.startsWith("/")) uri else "/$uri"

    companion object {
        fun getInstance(project: Project): RouteIndexService = project.service()
    }
}

package com.brighterly.testlinks.coverage

import com.brighterly.testlinks.settings.TestsLinksSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses PHPUnit Clover XML (default Laravel path: `build/logs/clover.xml`)
 * into a `Map<sourceFilePath, CoveragePercent>` that the gutter provider and
 * status bar consult.
 *
 * Clover structure used:
 *
 *   <coverage>
 *     <project>
 *       <file name="/abs/path/Foo.php">
 *         <metrics statements="12" coveredstatements="8" .../>
 *         ...
 *       </file>
 *       ...
 *     </project>
 *   </coverage>
 *
 * We compute covered/statements as a percentage. Not present = unknown.
 */
@Service(Service.Level.PROJECT)
class CloverCoverageService(private val project: Project) {

    private val logger = thisLogger()
    private val coverage = ConcurrentHashMap<String, Int>()

    @Volatile
    private var loaded: Boolean = false

    fun percentFor(path: String): Int? = coverage[path]

    fun isLoaded(): Boolean = loaded

    fun refresh() {
        ReadAction.nonBlocking<Unit> { load() }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun load() {
        val basePath = project.basePath ?: return
        val rel = TestsLinksSettings.get().state.cloverPath
        val file = File(basePath, rel)
        if (!file.isFile) {
            coverage.clear()
            loaded = false
            return
        }
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isNamespaceAware = false
            }
            val doc = factory.newDocumentBuilder().parse(file)
            val files: NodeList = doc.getElementsByTagName("file")
            coverage.clear()
            for (i in 0 until files.length) {
                val fileEl = files.item(i) as? Element ?: continue
                val name = fileEl.getAttribute("name").ifBlank { continue }
                val metrics = (fileEl.getElementsByTagName("metrics").item(0) as? Element)
                    ?: continue
                val statements = metrics.getAttribute("statements").toIntOrNull() ?: continue
                val covered = metrics.getAttribute("coveredstatements").toIntOrNull() ?: continue
                if (statements == 0) continue
                coverage[name] = (covered * 100) / statements
            }
            loaded = true
            logger.info("Clover coverage loaded: ${coverage.size} files")
        } catch (e: Exception) {
            logger.warn("Failed to parse clover XML at ${file.path}", e)
            coverage.clear()
            loaded = false
        }
    }

    companion object {
        fun getInstance(project: Project): CloverCoverageService = project.service()
    }
}

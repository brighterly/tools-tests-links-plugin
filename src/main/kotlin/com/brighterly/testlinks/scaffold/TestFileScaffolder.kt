package com.brighterly.testlinks.scaffold

import com.brighterly.testlinks.routes.RouteIndexService
import com.brighterly.testlinks.settings.TestsLinksSettings
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Pure functions that compute the path, namespace, and body of a new test file
 * for a given Service or Controller PHP class.
 *
 * Mapping rules (mirroring existing main-app conventions):
 *
 *   app/Services/{sub}/FooService.php          → tests/Services/{sub}/FooServiceTest.php
 *   app/Http/Controllers/{sub}/Bar.php         → tests/Controllers/{sub}/BarTest.php
 *
 * Test namespaces mirror the directory path under `tests/`:
 *
 *   Tests\Services\{sub}          or   Tests\Controllers\{sub}
 *
 * File bodies are loaded from classpath templates in `resources/templates/`
 * with simple `{{PLACEHOLDER}}` substitution — ServiceTest.php.stub for
 * services (method-level tests) and ControllerTest.php.stub for controllers
 * (HTTP endpoint tests). Templates can be edited without touching Kotlin code.
 */
object TestFileScaffolder {

    enum class Kind(val templateResource: String) {
        SERVICE("/templates/ServiceTest.php.stub"),
        CONTROLLER("/templates/ControllerTest.php.stub"),
    }

    data class Plan(
        val relativeTestPath: String,     // e.g. "tests/Services/Customer/FooServiceTest.php"
        val testNamespace: String,        // e.g. "Tests\\Services\\Customer"
        val testClassName: String,        // e.g. "FooServiceTest"
        val content: String,
        val kind: Kind,
    )

    internal data class Location(val dir: String, val namespace: String, val kind: Kind)

    fun planFor(phpClass: PhpClass, projectBasePath: String): Plan? {
        val sourceFile = phpClass.containingFile?.virtualFile ?: return null
        val sourcePath = sourceFile.path
        val className = phpClass.name.takeIf { it.isNotBlank() } ?: return null

        val normalizedBase = projectBasePath.trimEnd('/')
        val relativeSource = sourcePath.removePrefix("$normalizedBase/")

        val location = mapSourceToTestLocation(relativeSource) ?: return null

        val testClassName = "${className}Test"
        val relativeTestPath = "${location.dir}/$testClassName.php"
        val endpoint = if (location.kind == Kind.CONTROLLER) resolveEndpoint(phpClass, className) else ""
        val content = renderFromTemplate(
            kind = location.kind,
            namespace = location.namespace,
            testClassName = testClassName,
            sourceFqn = phpClass.fqn.removePrefix("\\"),
            sourceClassName = className,
            endpoint = endpoint,
        )

        return Plan(relativeTestPath, location.namespace, testClassName, content, location.kind)
    }

    internal fun mapSourceToTestLocation(relativeSource: String): Location? {
        val segments = relativeSource.split('/').dropLast(1)

        val servicesIdx = findSubPath(segments, listOf("app", "Services"))
        if (servicesIdx != -1) {
            val sub = segments.drop(servicesIdx + 2)
            val dir = (listOf("tests", "Services") + sub).joinToString("/")
            val ns = (listOf("Tests", "Services") + sub).joinToString("\\")
            return Location(dir, ns, Kind.SERVICE)
        }

        val controllersIdx = findSubPath(segments, listOf("app", "Http", "Controllers"))
        if (controllersIdx != -1) {
            val sub = segments.drop(controllersIdx + 3)
            val dir = (listOf("tests", "Controllers") + sub).joinToString("/")
            val ns = (listOf("Tests", "Controllers") + sub).joinToString("\\")
            return Location(dir, ns, Kind.CONTROLLER)
        }

        return null
    }

    private fun findSubPath(segments: List<String>, needle: List<String>): Int {
        if (needle.isEmpty() || segments.size < needle.size) return -1
        for (i in 0..segments.size - needle.size) {
            if (segments.subList(i, i + needle.size) == needle) return i
        }
        return -1
    }

    private fun renderFromTemplate(
        kind: Kind,
        namespace: String,
        testClassName: String,
        sourceFqn: String,
        sourceClassName: String,
        endpoint: String,
    ): String {
        val template = loadTemplate(kind.templateResource)
        return template
            .replace("{{NAMESPACE}}", namespace)
            .replace("{{TEST_CLASS}}", testClassName)
            .replace("{{SOURCE_FQN}}", sourceFqn)
            .replace("{{SOURCE_CLASS}}", sourceClassName)
            .replace("{{ENDPOINT}}", endpoint.ifBlank { "/api/todo-endpoint" })
    }

    private fun resolveEndpoint(phpClass: PhpClass, shortName: String): String {
        if (!TestsLinksSettings.get().state.enableRouteAwareStubs) return ""
        val project = phpClass.project
        val routes = RouteIndexService.getInstance(project)
        return routes.findUriFor(shortName) ?: ""
    }

    private fun loadTemplate(resourcePath: String): String {
        val stream = TestFileScaffolder::class.java.getResourceAsStream(resourcePath)
            ?: error("Template not found on classpath: $resourcePath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

package com.brighterly.testlinks.run

import com.intellij.openapi.project.Project

/**
 * Reads the last-known run state of a test class from PhpStorm's
 * TestStateStorage. Because the concrete class is part of an internal-ish
 * platform module we don't want to force a hard build-time dep on, we access
 * it reflectively — if it's missing at runtime we silently report UNKNOWN.
 *
 * URL conventions tried in order:
 *   - php_qn://{FQN}
 *   - java:suite://{FQN}
 *   - (for methods) php_qn://{FQN}::{method}
 */
object TestStateReader {

    enum class Status { PASSED, FAILED, IGNORED, UNKNOWN }

    private val storageClass: Class<*>? = runCatching {
        Class.forName("com.intellij.testIntegration.TestStateStorage")
    }.getOrNull()

    fun classStatus(project: Project, fqn: String): Status {
        if (storageClass == null) return Status.UNKNOWN
        val bare = fqn.removePrefix("\\")
        val candidates = listOf("php_qn://$bare", "java:suite://$bare", "php_qn://\\$bare")
        return tryUrls(project, candidates)
    }

    fun methodStatus(project: Project, classFqn: String, methodName: String): Status {
        if (storageClass == null) return Status.UNKNOWN
        val bare = classFqn.removePrefix("\\")
        val candidates = listOf(
            "php_qn://$bare::$methodName",
            "java:test://$bare/$methodName",
            "java:test://$bare.$methodName",
        )
        return tryUrls(project, candidates)
    }

    private fun tryUrls(project: Project, urls: List<String>): Status {
        val storage = runCatching {
            val method = storageClass!!.getMethod("getInstance", Project::class.java)
            method.invoke(null, project)
        }.getOrNull() ?: return Status.UNKNOWN

        for (url in urls) {
            val state = runCatching {
                val getState = storage.javaClass.getMethod("getState", String::class.java)
                getState.invoke(storage, url)
            }.getOrNull() ?: continue
            val magnitude = runCatching {
                state.javaClass.getField("magnitude").getInt(state)
            }.getOrElse {
                runCatching {
                    state.javaClass.getMethod("getMagnitude").invoke(state) as Int
                }.getOrNull()
            } ?: continue
            return decode(magnitude)
        }
        return Status.UNKNOWN
    }

    private fun decode(magnitude: Int): Status = when (magnitude) {
        8, 1 -> Status.PASSED
        6, 7 -> Status.FAILED
        0, 5 -> Status.IGNORED
        else -> Status.UNKNOWN
    }
}

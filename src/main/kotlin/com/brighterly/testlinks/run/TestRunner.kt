package com.brighterly.testlinks.run

import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Runs or debugs a PHPUnit test class/method. Resolves the appropriate run
 * configuration via the standard IntelliJ `ConfigurationContext` pipeline,
 * which the PHP plugin's PhpUnitConfigurationProducer contributes to.
 */
object TestRunner {

    private val logger = thisLogger()

    enum class Mode {
        RUN,
        DEBUG,
        RUN_WITH_COVERAGE,
    }

    fun run(project: Project, testFile: VirtualFile, mode: Mode) {
        val testClass = ReadAction.nonBlocking<com.jetbrains.php.lang.psi.elements.PhpClass?> {
            val psiFile = PsiManager.getInstance(project).findFile(testFile) ?: return@nonBlocking null
            findFirstPhpClass(psiFile)
        }
            .expireWith(project)
            .inSmartMode(project)
            .executeSynchronously() ?: run {
            logger.warn("No PhpClass found inside ${testFile.name}")
            return
        }
        runFromPsi(project, testClass, mode)
    }

    private fun findFirstPhpClass(psiFile: com.intellij.psi.PsiFile): com.jetbrains.php.lang.psi.elements.PhpClass? =
        PsiTreeUtil.findChildOfType(psiFile, com.jetbrains.php.lang.psi.elements.PhpClass::class.java)

    fun runFromPsi(project: Project, psiElement: PsiElement, mode: Mode) {
        val settings = ReadAction.nonBlocking<com.intellij.execution.RunnerAndConfigurationSettings?> {
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.PSI_ELEMENT, psiElement)
                .add(CommonDataKeys.PSI_FILE, psiElement.containingFile)
                .add(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement))
                .add(com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY, arrayOf(psiElement))
                .add(com.intellij.execution.Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement))
                .build()
            ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).configuration
        }
            .expireWith(project)
            .inSmartMode(project)
            .executeSynchronously() ?: run {
            logger.warn("No runnable configuration found for ${psiElement.javaClass.simpleName}")
            return
        }

        RunManager.getInstance(project).setTemporaryConfiguration(settings)

        val executor = when (mode) {
            Mode.RUN -> DefaultRunExecutor.getRunExecutorInstance()
            Mode.DEBUG -> DefaultDebugExecutor.getDebugExecutorInstance()
            // Coverage executor id is "Coverage" — resolve via executor registry with fallback.
            Mode.RUN_WITH_COVERAGE -> ExecutorRegistry.getInstance()
                .getExecutorById("Coverage")
                ?: DefaultRunExecutor.getRunExecutorInstance()
        }

        val runner = ProgramRunner.getRunner(executor.id, settings.configuration)
        if (runner == null) {
            logger.warn("No runner for executor ${executor.id}")
            return
        }

        val env = ExecutionEnvironmentBuilder
            .create(project, executor, settings.configuration)
            .runnerAndSettings(runner, settings)
            .build()

        ExecutionManager.getInstance(project).restartRunProfile(env)
    }
}

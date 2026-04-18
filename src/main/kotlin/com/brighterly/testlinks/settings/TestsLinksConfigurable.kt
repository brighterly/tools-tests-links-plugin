package com.brighterly.testlinks.settings

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class TestsLinksConfigurable : Configurable {

    private val servicePath = JTextField()
    private val controllerPath = JTextField()
    private val testsRoot = JTextField()
    private val cloverPath = JTextField()
    private val enableRunGutter = JCheckBox("Show Run/Debug in gutter right-click menu")
    private val enableLastRunStatus = JCheckBox("Overlay last-run status on gutter icon")
    private val enableStaleWarning = JCheckBox("Warn when subject modified after test")
    private val enableMethodMarkers = JCheckBox("Per-method gutter markers")
    private val enableRouteAwareStubs = JCheckBox("Use routes/*.php to prefill controller test endpoints")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Tests Links"
    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent("Service path prefix:", servicePath)
            .addLabeledComponent("Controller path prefix:", controllerPath)
            .addLabeledComponent("Tests root directory:", testsRoot)
            .addLabeledComponent("Clover coverage file (relative to project):", cloverPath)
            .addSeparator()
            .addComponent(enableRunGutter)
            .addComponent(enableLastRunStatus)
            .addComponent(enableStaleWarning)
            .addComponent(enableMethodMarkers)
            .addComponent(enableRouteAwareStubs)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = built
        reset()
        return built
    }

    override fun isModified(): Boolean {
        val s = TestsLinksSettings.get().state
        return servicePath.text != s.servicePathPrefix ||
            controllerPath.text != s.controllerPathPrefix ||
            testsRoot.text != s.testsRoot ||
            cloverPath.text != s.cloverPath ||
            enableRunGutter.isSelected != s.enableRunGutter ||
            enableLastRunStatus.isSelected != s.enableLastRunStatus ||
            enableStaleWarning.isSelected != s.enableStaleWarning ||
            enableMethodMarkers.isSelected != s.enableMethodMarkers ||
            enableRouteAwareStubs.isSelected != s.enableRouteAwareStubs
    }

    override fun apply() {
        val s = TestsLinksSettings.get().state
        s.servicePathPrefix = servicePath.text.ifBlank { "/app/Services/" }
        s.controllerPathPrefix = controllerPath.text.ifBlank { "/app/Http/Controllers/" }
        s.testsRoot = testsRoot.text.ifBlank { "tests" }
        s.cloverPath = cloverPath.text.ifBlank { "build/logs/clover.xml" }
        s.enableRunGutter = enableRunGutter.isSelected
        s.enableLastRunStatus = enableLastRunStatus.isSelected
        s.enableStaleWarning = enableStaleWarning.isSelected
        s.enableMethodMarkers = enableMethodMarkers.isSelected
        s.enableRouteAwareStubs = enableRouteAwareStubs.isSelected
    }

    override fun reset() {
        val s = TestsLinksSettings.get().state
        servicePath.text = s.servicePathPrefix
        controllerPath.text = s.controllerPathPrefix
        testsRoot.text = s.testsRoot
        cloverPath.text = s.cloverPath
        enableRunGutter.isSelected = s.enableRunGutter
        enableLastRunStatus.isSelected = s.enableLastRunStatus
        enableStaleWarning.isSelected = s.enableStaleWarning
        enableMethodMarkers.isSelected = s.enableMethodMarkers
        enableRouteAwareStubs.isSelected = s.enableRouteAwareStubs
    }
}

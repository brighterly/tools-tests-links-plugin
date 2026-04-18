package com.brighterly.testlinks.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "TestsLinksSettings",
    storages = [Storage("testsLinks.xml")],
)
class TestsLinksSettings : PersistentStateComponent<TestsLinksSettings.State> {

    data class State(
        var servicePathPrefix: String = "/app/Services/",
        var controllerPathPrefix: String = "/app/Http/Controllers/",
        var testsRoot: String = "tests",
        var cloverPath: String = "build/logs/clover.xml",
        var enableRunGutter: Boolean = true,
        var enableLastRunStatus: Boolean = true,
        var enableStaleWarning: Boolean = true,
        var enableMethodMarkers: Boolean = true,
        var enableRouteAwareStubs: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(newState: State) = XmlSerializerUtil.copyBean(newState, state)

    companion object {
        fun get(): TestsLinksSettings = service()
    }
}

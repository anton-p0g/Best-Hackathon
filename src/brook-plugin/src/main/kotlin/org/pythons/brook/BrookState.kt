package org.pythons.brook

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "BrookState",
    storages = [Storage("brook.xml")]
)
class BrookState : PersistentStateComponent<BrookState.StateData> {

    data class StateData(
        var specialty: String = "",
        var issuesInjected: Boolean = false,
        var sessionCount: Int = 0
    )

    private var data = StateData()

    // Convenience accessors
    var specialty: String
        get() = data.specialty
        set(value) { data.specialty = value }

    var issuesInjected: Boolean
        get() = data.issuesInjected
        set(value) { data.issuesInjected = value }

    var sessionCount: Int
        get() = data.sessionCount
        set(value) { data.sessionCount = value }

    override fun getState(): StateData = data

    override fun loadState(state: StateData) {
        data = state
    }

    companion object {
        fun getInstance(project: Project): BrookState = project.service()
    }
}

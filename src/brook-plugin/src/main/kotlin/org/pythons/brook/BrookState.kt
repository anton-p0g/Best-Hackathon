package org.pythons.brook

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class BrookState {

    var specialty: String = ""
    var issuesInjected: Boolean = false
    var sessionCount: Int = 0

    companion object {
        fun getInstance(project: Project): BrookState = project.service()
    }
}

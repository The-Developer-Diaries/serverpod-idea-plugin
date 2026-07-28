package dev.serverpod.idea.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "ServerpodSettings", storages = [Storage("serverpod.xml")])
class ServerpodSettings : SimplePersistentStateComponent<ServerpodSettings.State>(State()) {

    class State : BaseState() {
        var serverpodPath by string()
        var dartPath by string()
        var flutterPath by string()
        var dockerPath by string()
    }

    var serverpodPath: String?
        get() = state.serverpodPath
        set(value) {
            state.serverpodPath = value?.trim().takeUnless { it.isNullOrEmpty() }
        }

    var dartPath: String?
        get() = state.dartPath
        set(value) {
            state.dartPath = value?.trim().takeUnless { it.isNullOrEmpty() }
        }

    var flutterPath: String?
        get() = state.flutterPath
        set(value) {
            state.flutterPath = value?.trim().takeUnless { it.isNullOrEmpty() }
        }

    var dockerPath: String?
        get() = state.dockerPath
        set(value) {
            state.dockerPath = value?.trim().takeUnless { it.isNullOrEmpty() }
        }

    companion object {
        fun getInstance(): ServerpodSettings = service()
    }
}

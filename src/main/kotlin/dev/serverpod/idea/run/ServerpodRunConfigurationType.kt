package dev.serverpod.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import dev.serverpod.idea.ServerpodIcons

class ServerpodRunConfigurationType : ConfigurationTypeBase(
    ID,
    "Serverpod Server",
    "Runs a Serverpod server from its bin/main.dart entry point",
    NotNullLazyValue.createValue { ServerpodIcons.Logo },
) {
    init {
        addFactory(ServerpodConfigurationFactory(this))
    }

    companion object {
        const val ID = "ServerpodServerRunConfiguration"
    }
}

class ServerpodConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "Serverpod Server"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        ServerpodRunConfiguration(project, this, "Serverpod Server")

    override fun getOptionsClass(): Class<out BaseState> = ServerpodRunConfigurationOptions::class.java
}

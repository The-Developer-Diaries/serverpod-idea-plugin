package dev.serverpod.idea.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import dev.serverpod.idea.ServerpodIcons
import javax.swing.Icon

class ServerpodNewProjectWizard : GeneratorNewProjectWizard {

    override val id: String = "Serverpod"

    override val name: String = "Serverpod"

    override val icon: Icon = ServerpodIcons.Logo

    override val description: String =
        "Creates a Serverpod workspace with a Dart server, a generated client, and a Flutter app, " +
            "using the Serverpod CLI installed on this machine."

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::ServerpodWizardStep)
}

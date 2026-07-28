package dev.serverpod.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ServerpodStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        ServerpodProjectService.getInstance(project).detectNow()
    }
}

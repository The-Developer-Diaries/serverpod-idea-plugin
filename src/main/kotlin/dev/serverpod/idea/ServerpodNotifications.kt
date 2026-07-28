package dev.serverpod.idea

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.settings.ServerpodConfigurable

object ServerpodNotifications {

    private const val GROUP_ID = "Serverpod"

    fun info(project: Project?, title: String, content: String) =
        notify(project, title, content, NotificationType.INFORMATION)

    fun warn(project: Project?, title: String, content: String) =
        notify(project, title, content, NotificationType.WARNING)

    fun error(project: Project?, title: String, content: String) =
        notify(project, title, content, NotificationType.ERROR)

    /** A warning carrying fixes, each of which dismisses the balloon when invoked. */
    fun actionable(
        project: Project?,
        title: String,
        content: String,
        vararg actions: Pair<String, () -> Unit>,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, NotificationType.WARNING)

        actions.forEach { (text, action) ->
            notification.addAction(NotificationAction.createSimpleExpiring(text, action))
        }

        notification.notify(project)
    }

    fun missingTool(project: Project?, tool: CliTool) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                ServerpodBundle.message("notification.tool.missing.title", tool.displayName),
                ServerpodBundle.message("notification.tool.missing.content", tool.executableName),
                NotificationType.ERROR,
            )
        notification.addAction(object : com.intellij.openapi.actionSystem.AnAction(
            ServerpodBundle.message("notification.tool.missing.action")
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ServerpodConfigurable::class.java)
                notification.expire()
            }
        })
        notification.notify(project)
    }

    private fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}

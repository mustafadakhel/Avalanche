package com.mustafadakhel.avalanche

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.GitLocalBranch
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val GIT_BRANCHES_DATA_ID = "Git.Branches"

class EnableAutoUpdateAction : AnAction(), DumbAware {

    private val branches = mutableMapOf<String, GitRepository>()
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val logger = Logger.getInstance(EnableAutoUpdateAction::class.java)

    init {
        // Schedule the background task to fetch and update all branches in the map every 15 minutes
        scheduler.scheduleAtFixedRate({ fetchAndUpdateBranches() }, 0, 15, TimeUnit.MINUTES)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        // Get the selected Git repository and branch
        val repository = GitRepositoryManager.getInstance(e.project!!).repositories.firstOrNull()
        if (repository == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val currentBranch = repository.currentBranch?.name

        if (currentBranch == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val data = e.dataContext.getData(GIT_BRANCHES_DATA_ID)
        if (data == null || data !is List<*>) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        if (data.isEmpty() || data.size != 1) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        if (data.first() !is GitLocalBranch) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val branch = data.first() as GitLocalBranch

        e.presentation.isEnabledAndVisible = branch.name != currentBranch
        if (branches.containsKey(branch.name)) {
            e.presentation.text = "Disable Auto Update"
        } else {
            e.presentation.text = "Enable Auto Update"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Get the selected Git repository and branch
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        val data = e.dataContext.getData(GIT_BRANCHES_DATA_ID)
        if (data == null || data !is List<*>) return
        val branch = data.firstOrNull() as? GitLocalBranch ?: return

        if (repository != null) {
            if (branches.containsKey(branch.name)) {
                // Remove the branch from the map
                branches.remove(branch.name)
                project.showNotification("Auto Update Disabled for Branch: ${branch.name}")
                logger.info("Auto Update Disabled for Branch: ${branch.name}")
            } else {
                // Store the branch and repository in the map
                branches[branch.name] = repository
                project.showNotification("Auto Update Enabled for Branch: ${branch.name}")
                logger.info("Auto Update Enabled for Branch: ${branch.name}")
            }
        }
    }

    private fun fetchAndUpdateBranches() {
        // Fetch and update each branch in the data structure
        for ((branch, repository) in branches) {
            logger.info("Fetching and updating branch: $branch")

            // Get the project and Git root directory for the repository
            val project = repository.project

            // Check the current branch
            val currentBranch = repository.currentBranch?.name

            // Skip the update if the branch is the currently checked out branch
            if (branch == currentBranch) {
                logger.info("Skipping update for branch: $branch (currently checked out)")
                continue
            }

            // Get the remote repository (assuming the first remote is the target)
            val remote = GitUtil.getDefaultRemote(repository.remotes)
            if (remote == null) {
                logger.warn("No remote found for repository: ${repository.root}")
                continue
            }

            // Fetch the latest changes from the remote repository
            val fetchResult: GitCommandResult =
                Git.getInstance().fetch(repository, remote, emptyList<GitLineHandlerListener>())
            if (!fetchResult.success()) {
                // Display the error message to the user
                Messages.showErrorDialog(
                    project,
                    fetchResult.errorOutputAsJoinedString,
                    "Fetch Failed for Branch: $branch"
                )
                project.showNotification("Fetch Failed for Branch: $branch", NotificationType.ERROR)
                logger.error("Fetch failed for branch: $branch - ${fetchResult.errorOutputAsJoinedString}")
                continue
            }

            // Merge the fetched changes into the local branch
            val mergeResult: GitCommandResult =
                Git.getInstance().merge(repository, "refs/remotes/${remote.name}/$branch", emptyList())
            if (!mergeResult.success()) {
                // Display the error message to the user
                project.showNotification("Merge Failed for Branch: $branch", NotificationType.ERROR)
                logger.error("Merge failed for branch: $branch - ${mergeResult.errorOutputAsJoinedString}")
                continue
            }

            logger.info("Successfully updated branch: $branch")
        }
    }

    private fun Project.showNotification(
        content: String,
        type: NotificationType = NotificationType.INFORMATION
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Branch Auto Update Notification Group")
            .createNotification(content, type)
            .notify(this)
    }
}

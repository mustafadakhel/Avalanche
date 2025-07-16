package com.mustafadakhel.avalanche

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.util.ThreeState
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
@State(name = "AutoUpdateSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AutoUpdateSettingsService(private val project: Project) :
    PersistentStateComponent<AutoUpdateSettingsService.State> {

    data class BranchConfig(val root: String = "", val branch: String = "")

    enum class ConflictHandlingMode { SKIP, NOTIFY, AUTO_STASH }

    data class State(
        val branches: Set<BranchConfig> = setOf(),
        val intervalMinutes: Long = 15,
        val conflictHandlingMode: ConflictHandlingMode = ConflictHandlingMode.SKIP
    )

    private val logger = Logger.getInstance(AutoUpdateSettingsService::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null

    private var state = State()

    init {
        reschedule()
    }

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
        reschedule()
    }

    fun toggleBranch(repository: GitRepository, branch: String) {
        val config = BranchConfig(repository.root.path, branch)
        if (state.branches.contains(config)) {
            loadState(state.copy(branches = state.branches - config))
            project.showNotification("Auto Update Disabled for Branch: $branch")
        } else {
            loadState(state.copy(branches = state.branches + config))
            project.showNotification("Auto Update Enabled for Branch: $branch")
        }
    }

    fun isBranchEnabled(repository: GitRepository, branch: String): Boolean {
        val config = BranchConfig(repository.root.path, branch)
        return state.branches.contains(config)
    }

    private fun reschedule() {
        future?.cancel(false)
        future = scheduler.scheduleAtFixedRate({ fetchAndUpdateBranches() }, 0, state.intervalMinutes, TimeUnit.MINUTES)
    }

    private fun fetchAndUpdateBranches() {
        val manager = GitRepositoryManager.getInstance(project)
        for (config in state.branches) {
            val repository = manager.repositories.find { it.root.path == config.root } ?: continue
            updateBranch(repository, config.branch)
        }
    }

    private fun updateBranch(repository: GitRepository, branch: String) {
        logger.info("Fetching and updating branch: $branch")
        val project = repository.project
        val currentBranch = repository.currentBranch?.name
        if (branch == currentBranch) {
            logger.info("Skipping update for branch: $branch (currently checked out)")
            return
        }
        val changeListManager = ChangeListManager.getInstance(project)
        if (changeListManager.isFreezed != null) {
            logger.info("Skipping update for branch: $branch (change list manager is frozen)")
            return
        }
        // Skip if a merge or rebase is in progress
        if (repository.state == com.intellij.dvcs.repo.Repository.State.MERGING ||
            repository.state == com.intellij.dvcs.repo.Repository.State.REBASING
        ) {
            logger.info("Skipping update for branch: $branch (merge or rebase in progress)")
            return
        }
        val localBranch = repository.branches.findLocalBranch(branch) ?: run {
            logger.warn("Local branch not found: $branch in repository: ${repository.root}")
            project.showNotification("Branch Not Found: $branch", NotificationType.ERROR)
            return
        }

        // Skip if there are local modifications
        if (localBranch.hasLocalChanges(project, repository) != ThreeState.NO) {
            logger.info("Skipping update for branch: $branch (local changes present)")
            project.showNotification("Skipping update for $branch due to local modifications")
            return
        }
        val remote = GitUtil.getDefaultRemote(repository.remotes)
        if (remote == null) {
            logger.warn("No remote found for repository: ${repository.root}")
            return
        }
        val fetchResult: GitCommandResult =
            Git.getInstance().fetch(repository, remote, emptyList<GitLineHandlerListener>())
        if (!fetchResult.success()) {
            project.showNotification("Fetch Failed for Branch: $branch", NotificationType.ERROR)
            logger.error("Fetch failed for branch: $branch - ${fetchResult.errorOutputAsJoinedString}")
            return
        }
        val mergeResult: GitCommandResult =
            Git.getInstance().merge(repository, "refs/remotes/${remote.name}/$branch", emptyList())
        if (!mergeResult.success()) {
            project.showNotification("Merge Failed for Branch: $branch", NotificationType.ERROR)
            logger.error("Merge failed for branch: $branch - ${mergeResult.errorOutputAsJoinedString}")
            return
        }
        logger.info("Successfully updated branch: $branch")
    }

}

fun Project.showNotification(
    content: String,
    type: NotificationType = NotificationType.INFORMATION
) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Branch Auto Update Notification Group")
        .createNotification(content, type)
        .notify(this)
}


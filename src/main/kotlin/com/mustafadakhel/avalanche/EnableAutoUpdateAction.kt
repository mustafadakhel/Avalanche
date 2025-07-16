package com.mustafadakhel.avalanche

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import git4idea.GitLocalBranch
import git4idea.repo.GitRepositoryManager

private const val GIT_BRANCHES_DATA_ID = "Git.Branches"

class EnableAutoUpdateAction : AnAction(), DumbAware {
    private val logger = Logger.getInstance(EnableAutoUpdateAction::class.java)
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val branch = e.branchOrNull() ?: run {
            e.presentation.isEnabledAndVisible = false
            logger.warn("EnableAutoUpdateAction: Branch is null or not a GitLocalBranch")
            return
        }

        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull() ?: run {
            e.presentation.isEnabledAndVisible = false
            logger.warn("EnableAutoUpdateAction: No Git repository found in project")
            return
        }

        val service = project.getService(AutoUpdateSettingsService::class.java)
        e.presentation.isEnabledAndVisible = true
        if (service.isBranchEnabled(repository, branch.name)) {
            e.presentation.text = "Disable Auto Update"
        } else {
            e.presentation.text = "Enable Auto Update"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull() ?: run {
            logger.warn("EnableAutoUpdateAction: No Git repository found in project")
            return
        }
        val branch = e.branchOrNull() ?: run {
            logger.warn("EnableAutoUpdateAction: Branch is null or not a GitLocalBranch")
            return
        }

        val service = project.getService(AutoUpdateSettingsService::class.java)

        service.toggleBranch(repository, branch.name)
    }

    private fun AnActionEvent.branchOrNull(): GitLocalBranch? {
        val data = dataContext.getData(GIT_BRANCHES_DATA_ID)

        if (data == null || data !is List<*>) {
            presentation.isEnabledAndVisible = false
            logger.warn("EnableAutoUpdateAction: No branches found in data context")
            return null
        }
        if (data.isEmpty() || data.size != 1) {
            presentation.isEnabledAndVisible = false
            logger.warn("EnableAutoUpdateAction: Expected exactly one branch, found ${data.size}")
            return null
        }
        if (data.first() !is GitLocalBranch) {
            presentation.isEnabledAndVisible = false
            logger.warn("EnableAutoUpdateAction: First item in data is not a GitLocalBranch")
            return null
        }

        return data.first() as? GitLocalBranch
    }
}

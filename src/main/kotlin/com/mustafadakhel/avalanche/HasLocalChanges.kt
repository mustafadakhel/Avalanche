package com.mustafadakhel.avalanche

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.util.ThreeState
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

fun GitLocalBranch.hasLocalChanges(
    project: Project,
    repository: GitRepository,
): ThreeState {
    val changes = ChangeListManager.getInstance(project).allChanges
    if (changes.isEmpty())
        return ThreeState.NO

    val workTree = File(repository.root.path)
    val jgitRepo = try {
        FileRepositoryBuilder()
            .setWorkTree(workTree)
            .readEnvironment()
            .findGitDir()
            .build()
    } catch (e: Exception) {
        return ThreeState.UNSURE
    }

    val status = try {
        BranchTrackingStatus.of(jgitRepo, name)
    } catch (e: Exception) {
        null
    }

    return when {
        status == null -> ThreeState.UNSURE
        status.aheadCount > 0 -> ThreeState.YES
        else -> ThreeState.NO
    }
}


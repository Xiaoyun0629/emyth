/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.di

import android.content.Context
import dev.emyth.app.data.files.FileFolders
import dev.emyth.app.data.files.FilesManager
import dev.emyth.app.data.files.SkillManager
import dev.emyth.app.data.repository.ConversationRepository
import dev.emyth.app.data.repository.FavoriteRepository
import dev.emyth.app.data.repository.FilesRepository
import dev.emyth.app.data.repository.FolderRepository
import dev.emyth.app.data.repository.GenMediaRepository
import dev.emyth.app.data.repository.MemoryRepository
import dev.emyth.app.data.repository.WorkspaceRepository
import dev.emyth.app.data.security.SecurityAuditRepository
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
                extraBindMounts = listOf(
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                        target = "/skills",
                    ),
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                        target = "/upload",
                    ),
                ),
            )
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }

    single {
        SecurityAuditRepository(
            get<dev.emyth.app.data.db.AppDatabase>().securityAuditDao()
        )
    }
}
/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import me.rerere.ai.core.TokenUsage
import dev.emyth.app.data.db.dao.ConversationDAO
import dev.emyth.app.data.db.dao.FavoriteDAO
import dev.emyth.app.data.db.dao.FolderDAO
import dev.emyth.app.data.db.dao.GenMediaDAO
import dev.emyth.app.data.db.dao.ManagedFileDAO
import dev.emyth.app.data.db.dao.MemoryBankDAO
import dev.emyth.app.data.db.dao.MemoryDAO
import dev.emyth.app.data.db.dao.MessageNodeDAO
import dev.emyth.app.data.db.dao.WorkspaceDAO
import dev.emyth.app.data.db.entity.ConversationEntity
import dev.emyth.app.data.db.entity.FavoriteEntity
import dev.emyth.app.data.db.entity.FolderEntity
import dev.emyth.app.data.db.entity.GenMediaEntity
import dev.emyth.app.data.db.entity.ManagedFileEntity
import dev.emyth.app.data.db.entity.MemoryBankEntity
import dev.emyth.app.data.db.entity.MemoryEntity
import dev.emyth.app.data.db.entity.MessageNodeEntity
import dev.emyth.app.data.db.entity.SshHostEntity
import dev.emyth.app.data.db.entity.WorkspaceEntity
import dev.emyth.app.data.db.dao.SshHostDao
import dev.emyth.app.data.security.SecurityAuditDao
import dev.emyth.app.data.security.SecurityAuditEntity
import dev.emyth.app.workflow.db.WorkflowDao
import dev.emyth.app.workflow.db.WorkflowEntity
import dev.emyth.app.workflow.db.WorkflowRunDao
import dev.emyth.app.workflow.db.WorkflowRunEntity
import dev.emyth.app.data.db.migrations.Migration_16_17
import dev.emyth.app.data.db.migrations.Migration_24_25
import dev.emyth.app.data.db.migrations.Migration_8_9
import dev.emyth.app.utils.JsonInstant

@Database(
    entities = [
        ConversationEntity::class,
        MemoryEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        FavoriteEntity::class,
        MemoryBankEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
        WorkflowEntity::class,
        WorkflowRunEntity::class,
        SshHostEntity::class,
        SecurityAuditEntity::class,
    ],
    version = 29,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 16, to = 17, spec = Migration_16_17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun favoriteDao(): FavoriteDAO

    abstract fun memoryBankDao(): MemoryBankDAO

    abstract fun workspaceDao(): WorkspaceDAO

    abstract fun folderDao(): FolderDAO

    abstract fun workflowDao(): WorkflowDao

    abstract fun workflowRunDao(): WorkflowRunDao

    abstract fun sshHostDao(): SshHostDao

    abstract fun securityAuditDao(): SecurityAuditDao
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}

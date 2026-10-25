/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.repository

import dev.emyth.app.data.db.dao.SshHostDao
import dev.emyth.app.data.db.entity.SshHostEntity

class SshHostRepository(private val dao: SshHostDao) {
    suspend fun getAll(): List<SshHostEntity> = dao.getAll()
    suspend fun getByName(name: String): SshHostEntity? = dao.getByName(name)
    suspend fun upsert(host: SshHostEntity) = dao.upsert(host)
    suspend fun deleteByName(name: String) = dao.deleteByName(name)
}

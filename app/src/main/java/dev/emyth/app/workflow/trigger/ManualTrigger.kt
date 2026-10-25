/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.workflow.trigger

import dev.emyth.app.workflow.model.TriggerSpec
import dev.emyth.app.workflow.model.WorkflowDefinition

/**
 * Phase 12 — manual trigger. No receiver, no schedule. Fires only via `workflow_run` tool
 * or the Settings "Run now" button — both call straight into
 * [dev.emyth.app.workflow.execution.WorkflowEngine.fireNow].
 *
 * Lives as a family for symmetry — its sole job is to declare ownership of [TriggerSpec.Manual]
 * so [TriggerRegistry] doesn't think it's an unhandled type.
 */
internal class ManualTriggerFamily : WorkflowTriggerFamily {
    override val name = "manual"
    override fun handles(spec: TriggerSpec): Boolean = spec is TriggerSpec.Manual
    override suspend fun sync(matching: List<WorkflowDefinition>, callback: TriggerFireCallback) = Unit
    override suspend fun shutdown() = Unit
}

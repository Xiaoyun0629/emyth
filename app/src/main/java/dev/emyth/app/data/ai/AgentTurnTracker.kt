/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.ai

import android.content.Context
import kotlinx.coroutines.flow.first
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.ui.overlay.AutomationGlowOverlay
import org.koin.java.KoinJavaComponent.getKoin

/**
 * 记录当前 turn 里发生的自动化操作（tap/swipe/gesture）。
 * Emyth 用它触发「光圈」状态指示——AI 操控手机时屏幕边缘亮一圈光，表示「机正在操控」。
 */
object AgentTurnTracker {
    suspend fun recordAutomationAction() {
        try {
            val koin = getKoin()
            val settingsStore = koin.get<SettingsStore>()
            val context = koin.get<Context>()
            val settings = settingsStore.settingsFlowRaw.first()
            val glow = settings.systemToolsSetting
            if (glow.automationGlowEnabled) {
                AutomationGlowOverlay.show(context, glow.automationGlowColor.toInt())
            }
        } catch (e: Exception) {
            // 光圈失败不影响工具执行
        }
    }
}

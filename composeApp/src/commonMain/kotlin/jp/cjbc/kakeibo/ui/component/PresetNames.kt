package jp.cjbc.kakeibo.ui.component

import jp.cjbc.kakeibo.model.CategoryPreset
import org.jetbrains.compose.resources.getString

/** Names the preset category [preset] in the user's language, when it is created. */
internal suspend fun presetName(preset: CategoryPreset): String = getString(preset.label)

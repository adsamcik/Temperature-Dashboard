package com.adsamcik.temperaturedashboard.shared.i18n

import androidx.compose.runtime.Composable
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.shell_dashboard
import com.adsamcik.temperaturedashboard.core.ui.resources.shell_scan
import com.adsamcik.temperaturedashboard.core.ui.resources.shell_settings
import com.adsamcik.temperaturedashboard.shared.navigation.ShellDestination
import org.jetbrains.compose.resources.stringResource

/**
 * Localised label for a shell destination. Reads from
 * [com.adsamcik.temperaturedashboard.core.ui.resources.Res] (defined in
 * `core/ui/src/commonMain/composeResources/values/strings.xml`).
 */
@Composable
fun labelOf(dest: ShellDestination): String = stringResource(
    when (dest) {
        ShellDestination.Dashboard -> Res.string.shell_dashboard
        ShellDestination.Scan -> Res.string.shell_scan
        ShellDestination.Settings -> Res.string.shell_settings
    },
)

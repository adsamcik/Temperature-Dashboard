package com.adsamcik.temperaturedashboard.shared.i18n

import androidx.compose.runtime.Composable
import com.adsamcik.temperaturedashboard.shared.navigation.ShellDestination
import com.adsamcik.temperaturedashboard.shared.resources.Res
import com.adsamcik.temperaturedashboard.shared.resources.shell_dashboard
import com.adsamcik.temperaturedashboard.shared.resources.shell_scan
import com.adsamcik.temperaturedashboard.shared.resources.shell_settings
import org.jetbrains.compose.resources.stringResource

/**
 * Localised label for a shell destination.
 *
 * Compose Multiplatform Resources reads from
 * `commonMain/composeResources/values/strings.xml`, falling back to
 * `values-XX/strings.xml` siblings based on the user's system locale
 * (e.g. `values-cs/` for Czech).
 *
 * Migration pattern for the rest of the UI:
 *   1. Add a `<string name="my_key">English text</string>` entry in
 *      `commonMain/composeResources/values/strings.xml`.
 *   2. Add a Czech (or other) translation in `values-cs/strings.xml`.
 *   3. Replace `Text("English text")` with `Text(stringResource(Res.string.my_key))`.
 *
 * The migration is mechanical; the full sweep was deferred from v0.3.0
 * to keep the sprint focused. Shell destination labels are migrated as
 * the proof-of-concept.
 */
@Composable
fun labelOf(dest: ShellDestination): String = stringResource(
    when (dest) {
        ShellDestination.Dashboard -> Res.string.shell_dashboard
        ShellDestination.Scan -> Res.string.shell_scan
        ShellDestination.Settings -> Res.string.shell_settings
    },
)

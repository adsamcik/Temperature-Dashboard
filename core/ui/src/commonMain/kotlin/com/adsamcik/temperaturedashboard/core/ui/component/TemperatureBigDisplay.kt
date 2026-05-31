package com.adsamcik.temperaturedashboard.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.Celsius
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import kotlin.math.roundToInt

/**
 * Big-number temperature display, e.g. `22.4 °C` with `°C` typeset smaller
 * and aligned to the baseline.
 *
 * Passing null renders an em-dash so layouts stay stable across no-data states.
 */
@Composable
fun TemperatureBigDisplay(
    valueC: Double?,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    integerOnly: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        val displayValue = valueC?.let { Celsius(it).convertTo(unit) }
        val formatted = when {
            displayValue == null -> "—"
            integerOnly -> displayValue.roundToInt().toString()
            else -> formatOneDecimal(displayValue)
        }
        Text(
            text = formatted,
            color = color,
            fontWeight = FontWeight.Light,
            fontSize = 72.sp,
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = unit.symbol,
            color = color,
            modifier = Modifier.padding(start = TdashSpacing.xs, bottom = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

internal fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    val whole = rounded.toLong()
    val tenths = ((rounded - whole) * 10).roundToInt().let { if (it < 0) -it else it }
    return "$whole.$tenths"
}

/** Public formatting helpers, KMP-safe (no java.util.Locale). */
fun formatTemperature(value: Double, decimals: Int = 1): String = formatDecimal(value, decimals)

fun formatDecimal(value: Double, decimals: Int): String {
    val factor = generateSequence(1) { it * 10 }.drop(decimals).first().toDouble()
    val rounded = (value * factor).toLong()
    val sign = if (value < 0 && rounded == 0L) "-" else ""
    val whole = (kotlin.math.abs(rounded) / factor.toLong())
    val frac = kotlin.math.abs(rounded) % factor.toLong()
    val fracPadded = frac.toString().padStart(decimals, '0')
    return if (decimals == 0) "$sign$whole" else "$sign$whole.$fracPadded"
}

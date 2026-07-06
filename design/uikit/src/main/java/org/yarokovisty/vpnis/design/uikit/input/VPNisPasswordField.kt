package org.yarokovisty.vpnis.design.uikit.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Password text field for the VPNis design system.
 *
 * A convenience wrapper over [VPNisOutlinedTextField] pre-configured for password entry:
 * - [PasswordVisualTransformation] masks the value by default; an internal reveal toggle lets
 *   the user flip masking on/off.
 * - `keyboardOptions` is fixed to [KeyboardType.Password] so the OS hides the input from
 *   recent-apps screenshots and suppresses personalised dictionaries.
 * - `singleLine` is fixed to `true`.
 * - The reveal toggle draws its own eye glyph via [Canvas] so `:design:uikit` retains no
 *   dependency on `material-icons-*`.
 *
 * The trailing-icon slot is occupied by the reveal toggle and is therefore not exposed as a
 * parameter. Other icon requirements can be satisfied via the [leadingIcon] slot
 * (e.g. a lock icon from the calling module).
 *
 * **A11y:** the reveal [IconButton] carries a `contentDescription` ("Show password" /
 * "Hide password") that updates with the toggle state.
 *
 * @param value Current password text.
 * @param onValueChange Callback invoked on every text change.
 * @param modifier Optional [Modifier] applied to the field.
 * @param enabled Whether the field accepts user input.
 * @param label Optional floating label rendered with `bodyMedium`.
 * @param leadingIcon Optional leading icon slot (e.g. a lock icon supplied by the caller).
 * @param isError Whether the field is in error state (error colour role applied).
 * @param supportingText Optional text below the field, rendered with `bodyMedium`.
 *   Shown in error colour when [isError] is true.
 * @param initialPasswordVisible Initial visibility state — `false` masks the password on first
 *   composition. Exposed for preview and testing purposes; production callers typically use
 *   the default.
 */
@Composable
public fun VPNisPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    initialPasswordVisible: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(initialPasswordVisible) }
    val toggleDescription = if (passwordVisible) "Hide password" else "Show password"

    VPNisOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        leadingIcon = leadingIcon,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                EyeIcon(
                    visible = passwordVisible,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = toggleDescription },
                )
            }
        },
    )
}

/**
 * Canvas-drawn eye glyph used as the password-reveal toggle icon in [VPNisPasswordField].
 *
 * Draws a lens-shaped outline (two quadratic Bézier arcs) plus a filled pupil circle.
 * When [visible] is `false` a diagonal strike-through line is added to convey the
 * "password is hidden" state. Sized at 20 dp to fit the [IconButton] touch target.
 *
 * @param visible `true` = eye open (password revealed); `false` = eye with slash (masked).
 * @param tint Stroke and fill colour — caller passes [MaterialTheme.colorScheme.onSurfaceVariant].
 * @param modifier [Modifier] applied to the [Canvas]. Used by callers to attach semantics.
 */
@Composable
private fun EyeIcon(visible: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeW = 1.5.dp.toPx()
        val leftX = w * 0.08f
        val rightX = w * 0.92f
        val arcHeight = h * 0.3f

        // Lens outline: two quadratic Bézier arcs forming an almond/vesica shape.
        val outlinePath = Path().apply {
            moveTo(leftX, cy)
            quadraticTo(cx, cy - arcHeight, rightX, cy)
            quadraticTo(cx, cy + arcHeight, leftX, cy)
            close()
        }
        drawPath(
            path = outlinePath,
            color = tint,
            style = Stroke(
                width = strokeW,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Pupil (filled circle in the centre of the lens).
        drawCircle(
            color = tint,
            radius = h * 0.12f,
            center = Offset(cx, cy),
        )

        if (!visible) {
            // Diagonal strike-through to indicate the password is masked.
            drawLine(
                color = tint,
                start = Offset(w * 0.2f, h * 0.82f),
                end = Offset(w * 0.8f, h * 0.18f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }
    }
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldMaskedLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPasswordField(
            value = "SecretPassword123!",
            onValueChange = {},
            label = "Password",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldRevealedLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPasswordField(
            value = "SecretPassword123!",
            onValueChange = {},
            label = "Password",
            initialPasswordVisible = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldErrorLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPasswordField(
            value = "short",
            onValueChange = {},
            label = "Password",
            isError = true,
            supportingText = "Password must be at least 12 characters",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPasswordField(
            value = "SecretPassword123!",
            onValueChange = {},
            label = "Password",
            enabled = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldMaskedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisPasswordField(
            value = "SecretPassword123!",
            onValueChange = {},
            label = "Password",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldRevealedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisPasswordField(
            value = "SecretPassword123!",
            onValueChange = {},
            label = "Password",
            initialPasswordVisible = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPasswordFieldErrorDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisPasswordField(
            value = "short",
            onValueChange = {},
            label = "Password",
            isError = true,
            supportingText = "Password must be at least 12 characters",
        )
    }
}

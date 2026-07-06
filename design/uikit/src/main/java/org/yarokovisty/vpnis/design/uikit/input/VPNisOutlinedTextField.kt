package org.yarokovisty.vpnis.design.uikit.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Outlined text field for the VPNis design system.
 *
 * Wraps M3 [OutlinedTextField] and enforces:
 * - [label], [placeholder], and [supportingText] are rendered with `bodyMedium` typography.
 * - [isError] and [supportingText] are always threaded through together so the M3 error state
 *   (red outline + tinted supporting text) is never accidentally broken.
 *
 * Icon slots ([leadingIcon], [trailingIcon]) are caller-provided composables so `:design:uikit`
 * does not acquire a dependency on `material-icons-*`.
 *
 * **Stable API only:** this wrapper uses the `value` / `onValueChange` overload of
 * [OutlinedTextField]. The `TextFieldState`-based experimental overload is not exposed.
 *
 * @param value Current text field content.
 * @param onValueChange Callback invoked on every text change.
 * @param modifier Optional [Modifier] applied to the field.
 * @param enabled Whether the field accepts user input.
 * @param readOnly Whether the field is read-only (cursor shown, no editing).
 * @param label Optional floating label rendered with `bodyMedium`.
 * @param placeholder Optional hint text rendered with `bodyMedium` while the field is empty.
 * @param leadingIcon Optional leading icon slot. Caller provides the composable.
 * @param trailingIcon Optional trailing icon slot. Caller provides the composable.
 * @param isError Whether the field is in error state (error colour role applied).
 * @param supportingText Optional text below the field, rendered with `bodyMedium`.
 *   Shown in error colour when [isError] is true.
 * @param keyboardOptions Keyboard type, IME action, and capitalisation options.
 * @param keyboardActions Callbacks for IME actions (Done, Search, Next, etc.).
 * @param visualTransformation Transforms the displayed text (e.g. [androidx.compose.ui.text.input.PasswordVisualTransformation]).
 * @param singleLine When true, the field is constrained to a single horizontal line.
 */
@Composable
public fun VPNisOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = if (label != null) {
            { Text(text = label, style = MaterialTheme.typography.bodyMedium) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(text = placeholder, style = MaterialTheme.typography.bodyMedium) }
        } else {
            null
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(text = supportingText, style = MaterialTheme.typography.bodyMedium) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
    )
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldEmptyLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedTextField(
            value = "",
            onValueChange = {},
            label = "Username",
            placeholder = "your@email.com",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldFilledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldErrorLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedTextField(
            value = "not-an-email",
            onValueChange = {},
            label = "Username",
            isError = true,
            supportingText = "Enter a valid e-mail address",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
            enabled = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldEmptyDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedTextField(
            value = "",
            onValueChange = {},
            label = "Username",
            placeholder = "your@email.com",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldFilledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldErrorDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedTextField(
            value = "not-an-email",
            onValueChange = {},
            label = "Username",
            isError = true,
            supportingText = "Enter a valid e-mail address",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedTextFieldDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
            enabled = false,
        )
    }
}

/**
 * RTL sensitivity preview — locale "ar" triggers a right-to-left layout direction so that
 * leading/trailing icon positions are exercised in the mirrored direction.
 */
@Preview(showBackground = true, locale = "ar")
@Composable
private fun VPNisOutlinedTextFieldRtlPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
            leadingIcon = { PreviewIconDot() },
            trailingIcon = { PreviewIconDot() },
        )
    }
}

/** Simple filled circle used as an icon stand-in in previews (no material-icons dep). */
@Composable
private fun PreviewIconDot() {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = Color.Gray, radius = size.minDimension * 0.35f)
    }
}

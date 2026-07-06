package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.input.VPNisOutlinedTextField
import org.yarokovisty.vpnis.design.uikit.input.VPNisPasswordField
import org.yarokovisty.vpnis.design.uikit.input.VPNisTextField

/**
 * Showcase section for the input-fields family (`:design:uikit` `input/`).
 *
 * Demonstrates [VPNisTextField] (filled), [VPNisOutlinedTextField], and [VPNisPasswordField]
 * in their main states: default, with leading/trailing icons, error, and disabled.
 * State is held locally with [remember] so fields are interactive on-device.
 */
@Composable
fun InputShowcaseSection() {
    var filledValue by remember { mutableStateOf("") }
    var outlinedValue by remember { mutableStateOf("alice@vpnis.io") }
    var searchValue by remember { mutableStateOf("") }
    var errorValue by remember { mutableStateOf("not-an-email") }
    var passwordValue by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Filled TextField -----------------------------------------------
        Text("Filled — empty with placeholder", style = MaterialTheme.typography.labelMedium)
        VPNisTextField(
            value = filledValue,
            onValueChange = { filledValue = it },
            label = "Server address",
            placeholder = "e.g. vpn.example.com",
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Filled TextField with icons ------------------------------------
        Text("Filled — with leading icon", style = MaterialTheme.typography.labelMedium)
        VPNisTextField(
            value = searchValue,
            onValueChange = { searchValue = it },
            label = "Search servers",
            placeholder = "Country or city",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // --- Outlined TextField ---------------------------------------------
        Text("Outlined — filled", style = MaterialTheme.typography.labelMedium)
        VPNisOutlinedTextField(
            value = outlinedValue,
            onValueChange = { outlinedValue = it },
            label = "Username",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // --- Outlined TextField — error state --------------------------------
        Text("Outlined — error state", style = MaterialTheme.typography.labelMedium)
        VPNisOutlinedTextField(
            value = errorValue,
            onValueChange = { errorValue = it },
            label = "Username",
            isError = true,
            supportingText = "Enter a valid e-mail address",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // --- Outlined TextField — disabled -----------------------------------
        Text("Outlined — disabled", style = MaterialTheme.typography.labelMedium)
        VPNisOutlinedTextField(
            value = "alice@vpnis.io",
            onValueChange = {},
            label = "Username",
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Password field --------------------------------------------------
        Text("Password field", style = MaterialTheme.typography.labelMedium)
        VPNisPasswordField(
            value = passwordValue,
            onValueChange = { passwordValue = it },
            label = "Password",
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Password field with leading icon --------------------------------
        Text("Password field — with leading icon", style = MaterialTheme.typography.labelMedium)
        VPNisPasswordField(
            value = passwordValue,
            onValueChange = { passwordValue = it },
            label = "Master password",
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Password field — error state ------------------------------------
        Text("Password field — error", style = MaterialTheme.typography.labelMedium)
        VPNisPasswordField(
            value = "abc",
            onValueChange = {},
            label = "Password",
            isError = true,
            supportingText = "Password must be at least 12 characters",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

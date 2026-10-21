package com.openchamber.android.ui.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openchamber.android.core.model.FormField
import com.openchamber.android.i18n.LocalCatalog
import com.openchamber.android.core.model.FormRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The card that asks the user to fill in a form.
 *
 * The fields come from the engine, so the card renders whatever the form
 * declares: a text field, a number, a checkbox, a choice. A field the app
 * does not know is shown as read-only, so a form the phone cannot fill is
 * still visible instead of silently dropped.
 */
@Composable
fun FormCard(
    form: FormRequest,
    onReply: (JsonObject) -> Unit,
) {
    val values = remember(form.id) { mutableStateMapOf<String, String>() }
    val booleans = remember(form.id) { mutableStateMapOf<String, Boolean>() }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = form.title, style = MaterialTheme.typography.titleSmall)
            form.fields.forEach { field ->
                FormFieldRow(
                    field = field,
                    value = values[field.key].orEmpty(),
                    onValueChange = { values[field.key] = it },
                    checked = booleans[field.key] ?: (field.default?.let {
                        (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                    } ?: false),
                    onCheckedChange = { booleans[field.key] = it },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReply(buildAnswer(form, values, booleans)) }) {
                    Text(LocalCatalog.current.t("android.form.send"))
                }
            }
        }
    }
}

@Composable
private fun FormFieldRow(
    field: FormField,
    value: String,
    onValueChange: (String) -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    when (field.type) {
        "boolean" -> {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    text = field.title ?: field.key,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        "number", "integer" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.title ?: field.key) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        "select", "multiselect" -> {
            ChoiceField(field = field, value = value, onValueChange = onValueChange)
        }
        "string", "text" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.title ?: field.key) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 6,
            )
        }
        else -> {
            Text(
                text = "${field.title ?: field.key}: ${field.description.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceField(field: FormField, value: String, onValueChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = true }) {
            Text(field.title ?: field.key)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onValueChange(option.value)
                        open = false
                    },
                )
            }
        }
    }
}

private fun buildAnswer(
    form: FormRequest,
    values: Map<String, String>,
    booleans: Map<String, Boolean>,
): JsonObject = buildJsonObject {
    form.fields.forEach { field ->
        when (field.type) {
            "boolean" -> put(field.key, JsonPrimitive(booleans[field.key] ?: false))
            "number", "integer" -> {
                val raw = values[field.key]
                val number = raw?.toDoubleOrNull()
                if (number != null) put(field.key, JsonPrimitive(number)) else put(field.key, JsonPrimitive(raw.orEmpty()))
            }
            else -> put(field.key, JsonPrimitive(values[field.key].orEmpty()))
        }
    }
}

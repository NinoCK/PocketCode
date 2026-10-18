package io.github.ninock.pocketcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ninock.pocketcode.model.AgentQuestion
import io.github.ninock.pocketcode.model.AgentQuestionItem

/** Label used for the free-text choice next to the agent's options. */
private const val OtherChoice = "\u0000other"

/** The answer to one question: picked labels in option order, then the typed "Other" text. */
internal fun questionAnswer(item: AgentQuestionItem, picked: Set<String>, otherText: String): String? {
    val labels = item.options.map { it.label }.filter { it in picked }
    val other = otherText.trim().takeIf { OtherChoice in picked && it.isNotEmpty() }
    return (labels + listOfNotNull(other)).takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * Claude's AskUserQuestion prompt: one block per question with its options
 * (radio buttons, or checkboxes for multi-select) and an "Other" answer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuestionCard(
    question: AgentQuestion,
    onAnswer: (Map<String, String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = remember(question.requestId) { mutableStateMapOf<Int, Set<String>>() }
    val otherText = remember(question.requestId) { mutableStateMapOf<Int, String>() }
    val answers = question.questions.mapIndexed { index, item ->
        questionAnswer(item, picked[index].orEmpty(), otherText[index].orEmpty())
    }
    val complete = answers.all { it != null }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    polygon = MaterialShapes.Cookie6Sided,
                    size = 44.dp,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (question.questions.size == 1) "Claude has a question" else "Claude has ${question.questions.size} questions",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            question.questions.forEachIndexed { index, item ->
                QuestionBlock(
                    item = item,
                    picked = picked[index].orEmpty(),
                    otherText = otherText[index].orEmpty(),
                    onPick = { label ->
                        val current = picked[index].orEmpty()
                        picked[index] = when {
                            !item.multiSelect -> setOf(label)
                            label in current -> current - label
                            else -> current + label
                        }
                    },
                    onOtherText = { text ->
                        otherText[index] = text
                        // Typing an answer selects "Other".
                        if (text.isNotBlank()) {
                            val current = picked[index].orEmpty()
                            picked[index] = if (item.multiSelect) current + OtherChoice else setOf(OtherChoice)
                        }
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAnswer(null) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                Button(
                    onClick = {
                        onAnswer(question.questions.indices.associate { question.questions[it].question to answers[it].orEmpty() })
                    },
                    enabled = complete,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    item: AgentQuestionItem,
    picked: Set<String>,
    otherText: String,
    onPick: (String) -> Unit,
    onOtherText: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.header.isNotBlank()) {
            Text(item.header, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(item.question, style = MaterialTheme.typography.bodyLarge)
        if (item.multiSelect) {
            Text("Choose any", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(4.dp))
        item.options.forEach { option ->
            ChoiceRow(
                label = option.label,
                description = option.description,
                selected = option.label in picked,
                multiSelect = item.multiSelect,
                onClick = { onPick(option.label) },
            )
        }
        ChoiceRow(
            label = "Other",
            description = "",
            selected = OtherChoice in picked,
            multiSelect = item.multiSelect,
            onClick = { onPick(OtherChoice) },
        )
        if (OtherChoice in picked) {
            OutlinedTextField(
                value = otherText,
                onValueChange = onOtherText,
                placeholder = { Text("Your answer") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    description: String,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
) {
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(selection)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect) Checkbox(checked = selected, onCheckedChange = null) else RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

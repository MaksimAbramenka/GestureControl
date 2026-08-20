package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.voice.Command
import com.gesturecontrol.domain.voice.VoiceActivationState

private val MaxLabelWidth = 190.dp

@Composable
fun VoiceActivationLabel(
    activationState: VoiceActivationState,
    lastTranscript: String?,
    lastCommand: Command?,
    modifier: Modifier = Modifier,
) {
    val text = when (activationState) {
        is VoiceActivationState.Idle -> lastResultText(lastTranscript, lastCommand)
        is VoiceActivationState.SingleShotListening -> "Listening..."
    }
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = MaxLabelWidth)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun lastResultText(transcript: String?, command: Command?): String {
    if (command == null) return "Hold POINT and speak a command"
    if (transcript == null) return "Didn't catch that"
    val commandLabel = command.toDisplayLabel() ?: return "Heard \"$transcript\" -- not recognized"
    return "Heard \"$transcript\" -> $commandLabel"
}

private fun Command.toDisplayLabel(): String? = when (this) {
    is Command.SetBrushColor -> "color ${color.name.lowercase()}"
    is Command.SetBrushSize -> "size ${size.name.lowercase()}"
    Command.Undo -> "undo"
    Command.Redo -> "redo"
    Command.Clear -> "clear"
    Command.Save -> "save"
    Command.Unrecognized -> null
}

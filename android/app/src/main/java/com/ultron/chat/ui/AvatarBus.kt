package com.ultron.chat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridges the TTS engine (in the Activity) to the 3D avatar in Compose.
 * The Activity flips [speaking] on/off around an utterance; the avatar reads it
 * and switches the WebGL head into/out of its "speaking" animation.
 */
object AvatarBus {
    var speaking by mutableStateOf(false)
}

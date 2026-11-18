package com.example.xtreamtv

/**
 * Retro gaming integration foundation. This is a catalogue model, NOT an emulator.
 * Cores and redistribution-cleared games must be packaged and tested separately.
 */
enum class RetroSystem { NES, SNES, MEGA_DRIVE, GAME_BOY, GAME_BOY_COLOR, GBA, PS1 }
data class RetroGame(
    val title: String,
    val system: RetroSystem,
    val documentUri: String,
    val coverUri: String? = null
)
object RetroGameLibrary {
    fun systemForFile(name: String): RetroSystem? = when (name.substringAfterLast('.', "").lowercase()) {
        "nes" -> RetroSystem.NES
        "sfc", "smc" -> RetroSystem.SNES
        "md", "gen", "smd" -> RetroSystem.MEGA_DRIVE
        "gb" -> RetroSystem.GAME_BOY
        "gbc" -> RetroSystem.GAME_BOY_COLOR
        "gba" -> RetroSystem.GBA
        "cue", "chd" -> RetroSystem.PS1
        else -> null
    }
}

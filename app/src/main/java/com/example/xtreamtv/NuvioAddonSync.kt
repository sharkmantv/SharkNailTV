package com.example.xtreamtv

/**
 * A small, testable account-sync boundary for the Nuvio integration.
 * Nuvio's own source uses device-login RPCs and a Supabase `addons` table.
 * No credentials, private service keys, or unverified endpoints are embedded.
 */
data class SyncedNuvioAddon(
    val manifestUrl: String,
    val name: String,
    val enabled: Boolean,
    val sortOrder: Int
)

sealed interface NuvioSyncState {
    data object NotLinked : NuvioSyncState
    data class Linked(val accountLabel: String, val addons: List<SyncedNuvioAddon>) : NuvioSyncState
    data class Error(val message: String) : NuvioSyncState
}

interface NuvioAccountGateway {
    suspend fun startDeviceLink(): DeviceLinkInstructions
    suspend fun pollDeviceLink(deviceCode: String, nonce: String): NuvioSyncState
    suspend fun pullAddons(): List<SyncedNuvioAddon>
    suspend fun signOut()
}

data class DeviceLinkInstructions(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val nonce: String,
    val pollIntervalSeconds: Int
)

/** Fail closed until the Nuvio account endpoint and third-party access are verified. */
object UnconfiguredNuvioGateway : NuvioAccountGateway {
    override suspend fun startDeviceLink(): DeviceLinkInstructions =
        error("Nuvio account linking has not been configured or verified")
    override suspend fun pollDeviceLink(deviceCode: String, nonce: String): NuvioSyncState =
        NuvioSyncState.Error("Account linking unavailable")
    override suspend fun pullAddons(): List<SyncedNuvioAddon> =
        error("Account addon synchronization is not connected")
    override suspend fun signOut() = Unit
}

fun usableSyncedAddons(addons: List<SyncedNuvioAddon>): List<SyncedNuvioAddon> =
    addons.filter { it.enabled && it.manifestUrl.startsWith("https://") }
        .sortedBy { it.sortOrder }
        .distinctBy { it.manifestUrl.trimEnd('/') }

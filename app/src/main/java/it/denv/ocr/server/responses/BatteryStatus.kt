package it.denv.ocr.server.responses


@kotlinx.serialization.Serializable
data class BatteryStatus(
    val state: BatteryState,
    val level: Int
)

enum class BatteryState {
    CHARGING,
    FULL,
    DISCHARGING,
    NOT_CHARGING,
    UNKNOWN
}
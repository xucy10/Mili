package org.leavesmc.leaves.replay

data class BukkitRecorderOption(
    @JvmField var serverName: String = "Mili",
    @JvmField var forceWeather: BukkitRecordWeather = BukkitRecordWeather.NULL,
    @JvmField var forceDayTime: Int = -1,
    @JvmField var ignoreChat: Boolean = false
) {
    enum class BukkitRecordWeather {
        CLEAR,
        RAIN,
        THUNDER,
        NULL
    }
}

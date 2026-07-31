package android.media

import android.os.Handler

open class AudioDeviceCallback {
    open fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = Unit
    open fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = Unit
}

open class AudioDeviceInfo(val type: Int = TYPE_BUILTIN_SPEAKER) {
    companion object {
        const val TYPE_BUILTIN_SPEAKER = 2
        const val TYPE_BLUETOOTH_A2DP = 8
        const val TYPE_BLUETOOTH_SCO = 7
        const val TYPE_HEARING_AID = 23
        const val TYPE_BLE_HEADSET = 26
        const val TYPE_BLE_SPEAKER = 27
        const val TYPE_USB_DEVICE = 11
        const val TYPE_USB_HEADSET = 22
        const val TYPE_USB_ACCESSORY = 12
        const val TYPE_WIRED_HEADPHONES = 4
        const val TYPE_WIRED_HEADSET = 3
        const val TYPE_LINE_ANALOG = 5
        const val TYPE_LINE_DIGITAL = 6
        const val TYPE_HDMI = 9
        const val TYPE_HDMI_ARC = 10
        const val TYPE_HDMI_EARC = 29
        const val TYPE_DOCK = 13
        const val TYPE_REMOTE_SUBMIX = 25
    }
}

open class AudioManager {
    open fun registerAudioDeviceCallback(callback: AudioDeviceCallback, handler: Handler?) = Unit
    open fun unregisterAudioDeviceCallback(callback: AudioDeviceCallback) = Unit
    open fun getDevices(flags: Int): Array<AudioDeviceInfo> = emptyArray()
    open fun getAudioDevicesForAttributes(attributes: AudioAttributes): List<AudioDeviceInfo> = emptyList()

    companion object { const val GET_DEVICES_OUTPUTS: Int = 2 }
}

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(value: Int): Builder = this
        fun setContentType(value: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }

    companion object {
        const val USAGE_MEDIA: Int = 1
        const val CONTENT_TYPE_MUSIC: Int = 2
    }
}

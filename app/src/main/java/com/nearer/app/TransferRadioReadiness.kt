package com.nearer.app

/**
 * Gives actionable guidance without blocking Nearby Connections. Bluetooth can
 * support discovery/control while Wi-Fi is normally important for fast bulk data.
 * A null value means Android did not let us determine that radio's state.
 */
internal object TransferRadioReadiness {
    fun message(bluetoothEnabled: Boolean?, wifiEnabled: Boolean?): String? {
        if (bluetoothEnabled == true && wifiEnabled == true) return null
        if (bluetoothEnabled == false && wifiEnabled == false) {
            return "Turn on Bluetooth and Wi-Fi in your phone settings for the best NeaRer experience."
        }
        if (bluetoothEnabled == false) {
            return "Bluetooth is off. Turn it on for nearby discovery; Wi-Fi is ready for fast file transfers."
        }
        if (wifiEnabled == false) {
            return "Wi-Fi is off. Turn it on for fast file transfers; Bluetooth is ready for nearby discovery."
        }
        return "Check that Bluetooth and Wi-Fi are enabled in your phone settings for the best nearby connection."
    }
}

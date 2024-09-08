package me.huizengek.autopickup.preferences

object PhonePreferences : GlobalPreferencesHolder() {
    val enabledProperty = boolean(true)
    var enabled by enabledProperty

    var phoneToEar by boolean(true)
    var connectedBluetooth by boolean(true)

    var vibrate by boolean(true)
    var countdown by boolean(false)
}

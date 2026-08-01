package com.pararam2006.cmv.platform

expect class SystemService {
    fun isNotificationServiceSupported(): Boolean
    fun isNotificationServiceEnabled(): Boolean
    fun toggleService(isOn: Boolean): Boolean
    fun openNotificationSettings()
    fun searchWeb(query: String)
}
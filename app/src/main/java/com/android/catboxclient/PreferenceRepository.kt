package com.android.catboxclient

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("catbox_prefs", Context.MODE_PRIVATE)

    private val _userHash = MutableStateFlow(prefs.getString("user_hash", "") ?: "")
    val userHashFlow = _userHash.asStateFlow()

    fun getUserHash(): String = _userHash.value

    fun saveUserHash(hash: String) {
        prefs.edit().putString("user_hash", hash).apply()
        _userHash.value = hash
    }
}
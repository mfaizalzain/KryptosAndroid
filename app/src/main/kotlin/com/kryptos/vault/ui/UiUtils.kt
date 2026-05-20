package com.kryptos.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/**
 * Traverses context wrappers to find the base Activity.
 * Helpful for Jetpack Compose where LocalContext.current is frequently wrapped
 * inside Hilt contexts, themed wrapper contexts, or navigation contexts.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}

/**
 * Traverses context wrappers to find the base FragmentActivity.
 * Required for showing Fragment-level dialogs or prompts like Biometrics.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return context as? FragmentActivity
}

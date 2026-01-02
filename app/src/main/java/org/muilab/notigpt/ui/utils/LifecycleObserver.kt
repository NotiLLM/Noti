package org.muilab.notigpt.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun LifecycleObserver(
    lifecycleOwner: LifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
    onResume: () -> Unit,
    onPause: () -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
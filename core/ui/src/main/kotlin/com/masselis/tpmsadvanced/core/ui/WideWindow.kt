package com.masselis.tpmsadvanced.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The window is wider than tall, a phone in landscape. Height is then what's short: a page can
 * put its header beside its content, and the top bar can make way for it.
 */
@Composable
public fun isWideWindow(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

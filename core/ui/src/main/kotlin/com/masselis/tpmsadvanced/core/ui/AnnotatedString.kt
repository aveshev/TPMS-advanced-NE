package com.masselis.tpmsadvanced.core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Appends [text] in bold, a vehicle's name or a wheel's in a sentence */
public fun AnnotatedString.Builder.appendBold(text: String): Unit =
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }

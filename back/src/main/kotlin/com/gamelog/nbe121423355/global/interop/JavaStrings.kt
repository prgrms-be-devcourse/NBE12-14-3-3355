package com.gamelog.nbe121423355.global.interop

/** Match Java String.strip whitespace rules during migration. */
fun String.strip(): String = trim { java.lang.Character.isWhitespace(it) }

fun String.formatted(vararg args: Any?): String = java.lang.String.format(this, *args)

// Java String predicates use Character.isWhitespace; Kotlin also treats non-breaking spaces as
// whitespace.
fun String.javaIsBlank(): Boolean = codePoints().allMatch { Character.isWhitespace(it) }

// Java trim removes only characters at or below U+0020.
fun String.javaTrim(): String = trim { it <= ' ' }

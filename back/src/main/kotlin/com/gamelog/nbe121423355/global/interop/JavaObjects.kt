package com.gamelog.nbe121423355.global.interop

/** Preserves existing Objects.requireNonNull checks, including NullPointerException. */
fun <T : Any> javaNonNull(value: T?, message: String? = null): T =
    value ?: throw NullPointerException(message)

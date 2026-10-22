package com.gamelog.nbe121423355.global.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import java.lang.annotation.ElementType.FIELD

// 문자열의 UTF-8 바이트 길이 제한 (@Size는 글자 수 기준이라 한글 등 멀티바이트 문자를 못 막음)
// BCrypt는 72바이트 초과 비밀번호에 IllegalArgumentException을 던지므로 비밀번호 필드에 사용
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxByteLengthValidator::class])
annotation class MaxByteLength(
    val value: Int,
    val message: String = "입력값이 너무 깁니다.",
    val groups: Array<kotlin.reflect.KClass<*>> = [],
    val payload: Array<kotlin.reflect.KClass<out Payload>> = [],
)

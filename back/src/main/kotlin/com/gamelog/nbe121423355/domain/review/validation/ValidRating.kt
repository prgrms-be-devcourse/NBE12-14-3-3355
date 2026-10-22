package com.gamelog.nbe121423355.domain.review.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload

@MustBeDocumented
@Constraint(validatedBy = [RatingValidator::class])
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidRating(
    val message: String = "별점은 0.5 단위로 입력해야 합니다.",
    val groups: Array<kotlin.reflect.KClass<*>> = [],
    val payload: Array<kotlin.reflect.KClass<out Payload>> = [],
)

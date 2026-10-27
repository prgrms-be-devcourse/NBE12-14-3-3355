package com.gamelog.nbe121423355.global.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.nio.charset.StandardCharsets.UTF_8

class MaxByteLengthValidator : ConstraintValidator<MaxByteLength, String> {
    private var max = 0

    override fun initialize(constraintAnnotation: MaxByteLength) {
        this.max = constraintAnnotation.value
    }

    // null은 @NotBlank가 처리하므로 통과
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean =
        value == null || value.toByteArray(UTF_8).size <= max
}

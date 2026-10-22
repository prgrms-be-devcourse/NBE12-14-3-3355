package com.gamelog.nbe121423355.domain.review.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.math.BigDecimal
import java.math.BigDecimal.ZERO

class RatingValidator : ConstraintValidator<ValidRating, BigDecimal> {

    override fun isValid(rating: BigDecimal?, context: ConstraintValidatorContext?): Boolean {
        // 별점은 선택값이므로 null은 유효하다.
        if (rating == null) {
            return true
        }

        return rating.remainder(RATING_STEP).compareTo(BigDecimal.ZERO) == 0
    }

    companion object {
        private val RATING_STEP: BigDecimal = BigDecimal("0.5")
    }
}

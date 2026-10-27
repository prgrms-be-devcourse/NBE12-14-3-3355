package com.gamelog.nbe121423355.domain.review.dto.request

import jakarta.validation.Validation
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ReviewSaveRequestValidationTest {
    private val validatorFactory = Validation.buildDefaultValidatorFactory()
    private val validator = validatorFactory.validator

    @AfterEach
    fun closeValidatorFactory() {
        validatorFactory.close()
    }

    @Test
    fun contentOnlyReviewIsValid() {
        val request = ReviewSaveRequest(null, "별점 없이 작성한 리뷰", false)

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun ratingOnlyReviewIsValid() {
        val request = ReviewSaveRequest(BigDecimal("4.5"), null, false)

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun emptyReviewHasNoReviewContent() {
        val request = ReviewSaveRequest(null, "   ", false)

        assertThat(validator.validate(request)).isEmpty()
        assertThat(request.hasReviewContent()).isFalse()
    }
}

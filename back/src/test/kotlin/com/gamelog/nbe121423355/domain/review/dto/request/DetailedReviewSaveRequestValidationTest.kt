package com.gamelog.nbe121423355.domain.review.dto.request

import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class DetailedReviewSaveRequestValidationTest {
    private val validatorFactory = Validation.buildDefaultValidatorFactory()
    private val validator = validatorFactory.validator

    @AfterEach
    fun closeValidatorFactory() {
        validatorFactory.close()
    }

    @Test
    fun gameRecordWithoutReviewIsValid() {
        val request = DetailedReviewSaveRequest(emptyUserGameRequest(), null)

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun emptyReviewObjectIsValidAsGameRecordOnlyRequest() {
        val request = DetailedReviewSaveRequest(
            emptyUserGameRequest(), ReviewSaveRequest(null, "   ", false),
        )

        assertThat(validator.validate(request)).isEmpty()
        assertThat(requireNotNull(request.review).hasReviewContent()).isFalse()
    }

    private fun emptyUserGameRequest() = UserGameReqBody(
        playStatus = null,
        isPlaying = false,
        isBacklog = false,
        isWishlist = false,
        isLiked = false,
        platformId = null,
        playTimeHours = null,
        finishTimeHours = null,
        masterTimeHours = null,
        startedAt = null,
        completedAt = null,
        lastPlayedAt = null,
    )
}

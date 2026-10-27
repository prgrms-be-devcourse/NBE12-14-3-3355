package com.gamelog.nbe121423355.global.exceptionHandler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

// API로 재현하기 어려운 예외(동시 가입 경합, 예상하지 못한 오류)의 응답을 핸들러로 직접 확인
class GlobalExceptionHandlerFallbackTest {
    private val handler = GlobalExceptionHandler()

    @Test
    @DisplayName("DB 제약조건 위반은 DB 메시지를 숨기고 409-3으로 응답")
    fun dataIntegrityViolation_409() {
        val response =
            handler.handleException(DataIntegrityViolationException("Duplicate entry 'a@test.com' for key 'users.email'"))

        assertThat(response.resultCode).isEqualTo("409-3")
        assertThat(response.statusCode).isEqualTo(409)
        assertThat(response.msg).doesNotContain("Duplicate entry")
    }

    @Test
    @DisplayName("처리하지 못한 예외는 내부 메시지를 숨기고 500-1로 응답")
    fun unexpectedException_500() {
        val response = handler.handleException(IllegalStateException("connection password=secret"))

        assertThat(response.resultCode).isEqualTo("500-1")
        assertThat(response.statusCode).isEqualTo(500)
        assertThat(response.msg).doesNotContain("secret")
    }
}

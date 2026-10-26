package com.gamelog.nbe121423355.global.exceptionHandler

import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Spring MVC가 던지는 클라이언트 오류(4xx)가 500으로 처리되지 않는지 확인
// 인증 없이 호출 가능한(permitAll) 경로로 테스트
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
class GlobalExceptionHandlerTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
) {
    @Test
    @DisplayName("존재하지 않는 경로 요청 시 404")
    fun noResource_404() {
        mockMvc.perform(get("/api/v1/games/1/not-exist-path"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-0"))
    }

    @Test
    @DisplayName("PathVariable 타입 불일치 시 400")
    fun pathVariableTypeMismatch_400() {
        mockMvc.perform(get("/api/v1/games/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-0"))
    }

    @Test
    @DisplayName("RequestParam 타입 불일치 시 400")
    fun requestParamTypeMismatch_400() {
        mockMvc.perform(get("/api/v1/reviews/popular").param("size", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-0"))
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 요청 시 405")
    fun methodNotSupported_405() {
        mockMvc.perform(delete("/api/v1/users/signup"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.resultCode").value("405-0"))
    }
}

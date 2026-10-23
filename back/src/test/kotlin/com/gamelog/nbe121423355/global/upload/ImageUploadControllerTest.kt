package com.gamelog.nbe121423355.global.upload

import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Cloudinary 호출 전 단계(인증, 파일 검증)만 확인. 실제 업로드는 ImageUploadServiceTest에서 mock으로 검증
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
class ImageUploadControllerTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val jwtProvider: JwtProvider,
) {
    @Test
    @DisplayName("이미지 업로드 - 인증 없이 호출하면 401")
    fun uploadFailsWithoutAuthentication() {
        mockMvc.perform(multipart("/api/v1/uploads/images").file(imageFile()))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 빈 파일이면 400-4")
    fun uploadFailsForEmptyFile() {
        val emptyFile = MockMultipartFile("file", "empty.png", "image/png", ByteArray(0))

        mockMvc.perform(
            multipart("/api/v1/uploads/images")
                .file(emptyFile)
                .header("Authorization", "Bearer ${userToken()}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-4"))
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 이미지가 아닌 파일이면 400-5")
    fun uploadFailsForNonImage() {
        val textFile = MockMultipartFile("file", "note.txt", "text/plain", "hello".toByteArray())

        mockMvc.perform(
            multipart("/api/v1/uploads/images")
                .file(textFile)
                .header("Authorization", "Bearer ${userToken()}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-5"))
    }

    private fun imageFile() = MockMultipartFile("file", "image.png", "image/png", byteArrayOf(1, 2, 3))

    // 업로드 API는 DB 사용자를 조회하지 않으므로 토큰만 발급
    private fun userToken(): String = jwtProvider.generateAccessToken(1L, "USER")
}

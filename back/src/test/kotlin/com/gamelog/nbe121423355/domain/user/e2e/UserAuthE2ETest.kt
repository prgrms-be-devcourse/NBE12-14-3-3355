package com.gamelog.nbe121423355.domain.user.e2e

import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient

// 실제 서버를 띄우고 HTTP로 API를 호출해 로그인 세션의 처음부터 끝까지 검증
@SpringBootTest(webEnvironment = RANDOM_PORT)
@MySqlIntegrationTest
class UserAuthE2ETest {
    @Value("\${local.server.port}")
    private var port: Int = 0

    private lateinit var client: RestClient

    @BeforeEach
    fun setUp() {
        client =
            RestClient.builder()
                .baseUrl("http://localhost:$port")
                // 4xx/5xx도 예외 대신 응답으로 받아서 상태 코드를 검증
                .defaultStatusHandler({ true }) { _, _ -> }
                .build()
    }

    @Test
    @DisplayName("회원가입 → 로그인 → 내 정보 → 재발급 → 로그아웃 → 재발급 401")
    fun fullSessionLifecycle() {
        val email = uniqueEmail()
        signup(email)

        val login = login(email)
        assertThat(login.statusCode).isEqualTo(HttpStatus.OK)
        val accessToken = login.read<String>("$.data.accessToken")
        val refreshToken = login.refreshTokenCookie()

        val me = call(HttpMethod.GET, "/api/v1/users/me", bearer = accessToken)
        assertThat(me.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(me.read<String>("$.data.email")).isEqualTo(email)

        // Rotation: 재발급하면 accessToken과 refreshToken이 모두 새로 내려옴
        val refreshed = call(HttpMethod.POST, "/api/v1/users/refresh", cookie = refreshToken)
        assertThat(refreshed.statusCode).isEqualTo(HttpStatus.OK)
        val newAccessToken = refreshed.read<String>("$.data.accessToken")
        val rotatedRefreshToken = refreshed.refreshTokenCookie()
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken)

        assertThat(call(HttpMethod.GET, "/api/v1/users/me", bearer = newAccessToken).statusCode)
            .isEqualTo(HttpStatus.OK)

        val logout = call(HttpMethod.POST, "/api/v1/users/logout", cookie = rotatedRefreshToken)
        assertThat(logout.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(logout.setCookie()).contains("Max-Age=0")

        val afterLogout = call(HttpMethod.POST, "/api/v1/users/refresh", cookie = rotatedRefreshToken)
        assertThat(afterLogout.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(afterLogout.read<String>("$.resultCode")).isEqualTo("401-2")
    }

    @Test
    @DisplayName("refreshToken을 Bearer 헤더로 보내면 인증되지 않는다")
    fun refreshTokenIsNotAcceptedAsBearer() {
        val email = uniqueEmail()
        signup(email)
        val refreshToken = login(email).refreshTokenCookie()

        val response = call(HttpMethod.GET, "/api/v1/users/me", bearer = refreshToken)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.read<String>("$.resultCode")).isEqualTo("401-1")
    }

    @Test
    @DisplayName("회원탈퇴 → 재발급 401 → 같은 계정으로 로그인 401 → 같은 이메일로 재가입 가능")
    fun withdrawalEndsSessionAndFreesEmail() {
        val email = uniqueEmail()
        signup(email)
        val login = login(email)
        val accessToken = login.read<String>("$.data.accessToken")
        val refreshToken = login.refreshTokenCookie()

        val withdraw =
            call(HttpMethod.DELETE, "/api/v1/users/me", body = """{"password": "$PASSWORD"}""", bearer = accessToken)
        assertThat(withdraw.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(withdraw.read<String>("$.resultCode")).isEqualTo("200-19")
        assertThat(withdraw.setCookie()).contains("Max-Age=0")

        assertThat(call(HttpMethod.POST, "/api/v1/users/refresh", cookie = refreshToken).statusCode)
            .isEqualTo(HttpStatus.UNAUTHORIZED)

        val relogin = login(email)
        assertThat(relogin.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(relogin.read<String>("$.resultCode")).isEqualTo("401-1")

        // 탈퇴 시 이메일이 익명화되므로 같은 이메일로 다시 가입할 수 있음
        assertThat(signup(email).statusCode).isEqualTo(HttpStatus.CREATED)
    }

    private fun signup(email: String): ResponseEntity<String> =
        call(
            HttpMethod.POST,
            "/api/v1/users/signup",
            body = """{"nickname": "${email.substringBefore("@")}", "email": "$email", "password": "$PASSWORD"}""",
        )

    private fun login(email: String): ResponseEntity<String> =
        call(HttpMethod.POST, "/api/v1/users/login", body = """{"email": "$email", "password": "$PASSWORD"}""")

    private fun call(
        method: HttpMethod,
        path: String,
        body: String? = null,
        bearer: String? = null,
        cookie: String? = null,
    ): ResponseEntity<String> {
        val request = client.method(method).uri(path)
        bearer?.let { request.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        cookie?.let { request.header(HttpHeaders.COOKIE, "$COOKIE_NAME=$it") }
        body?.let { request.contentType(MediaType.APPLICATION_JSON).body(it) }
        return request.retrieve().toEntity(String::class.java)
    }

    private fun <T> ResponseEntity<String>.read(path: String): T = JsonPath.read(body, path)

    private fun ResponseEntity<String>.setCookie(): String =
        headers[HttpHeaders.SET_COOKIE].orEmpty().first { it.startsWith("$COOKIE_NAME=") }

    private fun ResponseEntity<String>.refreshTokenCookie(): String =
        setCookie().substringAfter("$COOKIE_NAME=").substringBefore(";")

    // 닉네임 길이 제한을 넘지 않게 짧은 랜덤값 사용
    private fun uniqueEmail() = "e2e${UUID.randomUUID().toString().take(8)}@test.com"

    companion object {
        private const val PASSWORD = "password123"
        private const val COOKIE_NAME = "refreshToken"
    }
}

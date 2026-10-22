package com.gamelog.nbe121423355.domain.game.client

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.global.interop.formatted
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.TEXT_PLAIN
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class IgdbClient(
    @param:Value("\${igdb.client-id}") private val clientId: String,
    @Value("\${igdb.client-secret}") private val clientSecret: String?,
) {
    private val restClient: RestClient

    @get:Synchronized
    private var accessToken: String? = null
        // 유효한 토큰이 있으면 재사용하고, 없으면 발급합니다.
        get() {
            if (field != null && Instant.now().isBefore(tokenExpiresAt)) {
                return field
            }

            val form = LinkedMultiValueMap<String, String>()
            form.add("client_id", clientId)
            form.add("client_secret", clientSecret)
            form.add("grant_type", "client_credentials")

            val requestedAt = Instant.now()

            val response =
                restClient
                    .post()
                    .uri("https://id.twitch.tv/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body<TokenResponse>(TokenResponse::class.java)

            kotlin.check(
                !(response == null ||
                    response.access_token == null ||
                    response.access_token.javaIsBlank() ||
                    response.expires_in == null ||
                    response.expires_in <= 0)
            ) {
                "Twitch 토큰 발급 응답이 올바르지 않습니다."
            }

            field = response.access_token

            // 실제 만료보다 60초 일찍 새 토큰을 받도록 합니다.
            tokenExpiresAt = requestedAt.plusSeconds(Math.max(0, response.expires_in - 60))

            return field
        }

    private var tokenExpiresAt: Instant? = Instant.EPOCH

    init {

        val factory =
            JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            )
        factory.setReadTimeout(Duration.ofSeconds(30))
        this.restClient = RestClient.builder().requestFactory(factory).build()
    }

    // ID 커서로 페이지를 순회하여 수집 중 목록 변경에 따른 offset 누락을 피합니다.
    @Synchronized
    fun fetchPage(afterId: Long, since: Long?, cutoff: Long): MutableList<IgdbGameResponse> {
        val query = IgdbClient.buildQuery(afterId, since, cutoff)
        var attempt = 0
        while (true) {
            try {
                Thread.sleep(300) // 이 클라이언트의 요청 속도를 초당 4회 미만으로 제한
                return requestGames(query)
            } catch (e: RestClientResponseException) {
                if (
                    attempt >= 3 ||
                        !(e.statusCode.value() == 429 || e.statusCode.is5xxServerError())
                )
                    throw e
                try {
                    Thread.sleep(2000L shl attempt)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("수집 중단", interrupted)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("수집 중단", e)
            }
            attempt++
        }
    }

    private fun requestGames(query: String): MutableList<IgdbGameResponse> {
        val token = this.accessToken

        val games =
            restClient
                .post()
                .uri("https://api.igdb.com/v4/games")
                .header("Client-ID", clientId)
                .headers(
                    java.util.function.Consumer { headers ->
                        headers.setBearerAuth(requireNotNull(token))
                    }
                )
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body<MutableList<IgdbGameResponse>>(
                    object : ParameterizedTypeReference<MutableList<IgdbGameResponse>>() {}
                )

        checkNotNull(games) { "IGDB 게임 목록 응답이 비어 있습니다." }

        return games
    }

    @JvmRecord
    data class TokenResponse(
        val access_token: String?,
        val expires_in: Long?,
        val token_type: String?,
    )

    companion object {
        @JvmStatic
        fun buildQuery(afterId: Long, since: Long?, cutoff: Long): String {
            val window =
                if (since == null) "updated_at <= " + cutoff
                else
                    "((updated_at >= " +
                        since +
                        " & updated_at <= " +
                        cutoff +
                        ") | (first_release_date >= " +
                        since +
                        " & first_release_date <= " +
                        cutoff +
                        "))"
            // 범위 밖으로 수정된 기존 게임도 삭제할 수 있도록 출시일 필터는 저장 단계에서 적용합니다.
            return """
        fields name, summary, cover.url, first_release_date, rating,
               involved_companies.developer,
               involved_companies.company.name,
               genres.name, platforms.name, collections.name;
        sort id asc;
        limit 500;
        where id > %d & %s;

        """
                .formatted(afterId, window)
        }
    }
}

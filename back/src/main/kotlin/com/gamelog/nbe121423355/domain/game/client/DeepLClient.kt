package com.gamelog.nbe121423355.domain.game.client

import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.strip
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class DeepLClient(@Value("\${DEEPL_API_KEY:}") key: String) {
    private val client: RestClient
    private val key: String

    init {
        this.key = key.strip()
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(10000)
        factory.setReadTimeout(60000)
        client =
            RestClient.builder()
                .requestFactory(factory)
                .baseUrl(
                    if (this.key.endsWith(":fx")) "https://api-free.deepl.com"
                    else "https://api.deepl.com"
                )
                .defaultHeader("Authorization", "DeepL-Auth-Key " + this.key)
                .build()
    }

    fun translate(source: String): String {
        check(!key.javaIsBlank()) { "DEEPL_API_KEY를 설정하세요." }
        val usage = client.get().uri("/v2/usage").retrieve().body<Usage>(Usage::class.java)
        val length = source.codePointCount(0, source.length)
        check(
            !(usage == null ||
                usage.character_count == null ||
                usage.character_limit == null ||
                length > usage.character_limit - usage.character_count)
        ) {
            "DeepL 잔여 한도가 부족하거나 확인되지 않습니다."
        }
        val result =
            client
                .post()
                .uri("/v2/translate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("text", java.util.List.of(source), "target_lang", "KO"))
                .retrieve()
                .body<Result>(Result::class.java)
        val translated = result?.translations?.singleOrNull()?.text
        check(translated != null && !translated.javaIsBlank()) { "DeepL 번역 응답이 올바르지 않습니다." }
        return translated
    }

    @JvmRecord data class Usage(val character_count: Long?, val character_limit: Long?)

    @JvmRecord data class Result(val translations: MutableList<Translation>?)

    @JvmRecord data class Translation(val text: String?)
}

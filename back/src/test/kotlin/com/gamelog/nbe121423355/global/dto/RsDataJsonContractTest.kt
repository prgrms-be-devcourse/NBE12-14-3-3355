package com.gamelog.nbe121423355.global.dto

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRequest
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewLikeResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class RsDataJsonContractTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `일반 Kotlin DTO도 공통 응답 필드와 Boolean 이름을 유지한다`() {
        // given: Boolean 필드가 있는 DTO를 공통 응답으로 감싼다.
        val response = RsData("200-1", "좋아요 상태", ReviewLikeResponse(10L, 3L, true))

        // when: 응답을 JSON으로 직렬화한다.
        val json = mapper.readTree(mapper.writeValueAsString(response))

        // then: 응답 필드와 liked 이름은 유지하고 내부 HTTP 상태 계산값은 노출하지 않는다.
        val expected = mapper.readTree("""{"resultCode":"200-1","msg":"좋아요 상태","data":{"reviewId":10,"likeCount":3,"liked":true}}""")
        assertThat(json).isEqualTo(expected)
        assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `본문이 없는 응답과 생략된 관리자 요청 기본값을 유지한다`() {
        // given: data가 없는 공통 응답과 선택 필드가 생략된 요청 JSON을 준비한다.
        val response = RsData<Unit>("200-1", "완료")
        val body = """{"type":"ALL"}"""

        // when: 응답을 직렬화하고 요청을 일반 Kotlin DTO로 역직렬화한다.
        val json = mapper.readTree(mapper.writeValueAsString(response))
        val request = mapper.readValue(body, AdminRelatedGamePrecomputeRequest::class.java)

        // then: null data와 선택 필드의 null 및 false 기본값을 보존한다.
        assertThat(json).isEqualTo(mapper.readTree("""{"resultCode":"200-1","msg":"완료","data":null}"""))
        assertThat(request.type).isEqualTo(RelatedGamePrecomputeTargetType.ALL)
        assertThat(request.gameIds).isNull()
        assertThat(request.sourceRunId).isNull()
        assertThat(request.initializeBaseline).isFalse()
    }
}

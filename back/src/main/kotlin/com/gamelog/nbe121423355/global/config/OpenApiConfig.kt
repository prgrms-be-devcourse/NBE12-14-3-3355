package com.gamelog.nbe121423355.global.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info =
        Info(
            title = "GameLog API",
            version = "v1",
            description = """
                GameLog 게임 탐색·기록·리뷰·추천·알림 REST API입니다.
                응답 본문은 resultCode, msg, data로 구성되며 HTTP 상태와 resultCode를 함께 확인합니다.
                1. 로그인 응답 data.accessToken을 Authorize에 입력합니다(Bearer 접두사는 자동 추가).
                2. 보호된 API는 accessToken, 재발급·로그아웃은 브라우저의 HttpOnly refreshToken 쿠키를 사용합니다.
                3. ADMIN 전용 표시는 실제 관리자 계정으로 로그인해야 하며 Authorize만으로 권한이 부여되지는 않습니다.
                일반 페이지는 각 API의 page·size를, 커서 목록은 응답의 nextCursor를 해당 커서 파라미터에 전달합니다.
                201은 생성, 202는 비동기 접수입니다. 202 응답만으로 작업 완료를 판단하지 말고 상태 조회 API를 사용하세요.
                Swagger 문서는 호출 안내이며 실제 요청 검증과 권한 판정은 서버의 기존 규칙을 따릅니다.
            """,
            contact = Contact(name = "GameLog Team"),
        )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "로그인 후 발급받은 accessToken을 입력하세요. Bearer 접두사는 자동으로 붙습니다.",
)
class OpenApiConfig

# Kotlin 전환 구현 기록

전체 운영 코드 전환을 하나의 PR로 반영한다. 작업 중 커밋은 빌드 지원·전환·호환성 보완·검증 결과로 구분할 수 있다. Java 테스트는 유지하고 검증 내용·기대값은 변경하지 않는다.

## 1. Kotlin 빌드 지원

- Kotlin JVM·Spring·JPA 플러그인을 2.4.20으로 통일한다.
- 기존 Java 25, Spring Boot 4.1.1, Gradle 9.7.1을 유지한다.
- `kotlin-reflect`와 Boot 4의 Jackson 3용 `tools.jackson.module:jackson-module-kotlin`을 추가한다. 버전 정렬은 Boot Gradle 플러그인과 BOM을 따른다.
- Kotlin도 JVM toolchain 25를 사용하고 JSR-305 nullability 정보를 엄격하게 처리한다.
- 임시 Kotlin 클래스로 실제 컴파일 후 class major version 69(Java 25)를 확인했다. 해당 임시 소스는 제거했다.
- 새 플러그인·의존성 추가 후 `clean build` 성공(1분 23초), 기존 Java 테스트 515개 통과(실패·오류·건너뜀 0). 운영 소스 전환 전의 빌드 지원 검증이다.
- 커밋 메시지: `build: Kotlin JVM 및 Spring JPA 빌드 지원 추가`

호환성 참고:

- [Kotlin Gradle 호환성](https://kotlinlang.org/docs/gradle-configure-project.html): 2.4.20의 fully-supported Gradle 범위 상한은 9.7.0이다. 현재 9.7.1을 유지하므로 실제 빌드 검증 결과를 함께 기록한다.
- [Java 25 지원](https://kotlinlang.org/docs/whatsnew23.html)
- [Spring Boot Kotlin 지원](https://docs.spring.io/spring-boot/reference/features/kotlin.html)
- [JPA all-open/no-arg 지원](https://kotlinlang.org/docs/whatsnew2320.html)

## 전환 도구 사용 원칙

- 자동 변환은 초기 초안 생성에만 활용하고 컴파일·동작 보존의 근거로 삼지 않는다.
- 사용자 IDE 설정과 분리된 임시 headless 환경에서 설치된 JetBrains J2K 변환기를 검증한다. Lombok 생성 코드도 확인하여 생성자·getter·정적 팩토리 누락을 방지한다.
- 변환 결과는 임시 경로에서 확인한 뒤 적용한다. DTO의 record 접근자, 엔티티 동등성·JPA 매핑, null 허용 범위, validation·JSON, CLI 진입점은 별도로 검증한다.

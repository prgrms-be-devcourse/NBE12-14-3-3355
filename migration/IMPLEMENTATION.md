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

## 2. 운영 Java 전체 전환

- 운영 Java 232개를 같은 패키지의 Kotlin 파일로 전환한다. Java 테스트 72개는 유지한다.
- Spring Boot 진입 클래스와 추천 CLI 클래스명을 유지하고 `@JvmStatic main`을 제공한다. `recommendationPrecompute` Gradle 명령·옵션·환경변수는 그대로 사용한다.
- Java record DTO는 가능한 경우 `@JvmRecord data class`로 옮겨 Java의 `id()` 등 기존 호출을 유지한다. 원본의 추가 생성자도 복원한다.
- 검색 요청의 null 페이지·크기 정규화와 신규 게임 이벤트의 방어적 목록 복사는 일반 클래스에서 보존한다.
- JPA 엔티티는 일반 클래스로 유지한다. Kotlin JPA 플러그인의 no-arg/all-open을 사용하며 기존 복합키의 동등성·해시 계산을 유지한다.
- 영속 필드명·컬럼·타입을 보존한다. `playing` 등 Boolean backing field는 기존 이름을 사용하고 `isPlaying` 계산 getter로 Java 호출을 유지한다. Auditing 시각은 변경 가능한 프로퍼티로 둔다.
- 엔티티·복합키·MappedSuperclass 33개의 필드명, enum 상수, 테이블·컬럼·인덱스·제약을 원본과 대조했다. assigned ID인 `IgdbSyncState.id`도 원본의 boxed Long을 유지한다.
- `@Schema` 설명·예시를 필드에 복원하고 목록 요소의 `@NotNull`·`@Positive`를 유지한다. JVM TYPE_USE 출력을 활성화한다.
- 기존 Java `Objects.requireNonNull`은 같은 NullPointerException 동작을 제공하는 `javaNonNull`로 옮긴다. Java `String.strip`·`trim`·`isBlank`의 공백 판정과 `formatted` 호출은 작은 호환 함수로 유지한다. 소스의 강제 non-null assertion은 사용하지 않는다.
- 추천 점수·순위·제외·온보딩 전환, nullable 별점·리뷰·IGDB 데이터, refreshToken의 누락 클레임 처리를 기존 테스트로 확인한다.
- 운영 Lombok 의존성과 annotation processor는 제거한다. 테스트의 Lombok 설정은 유지한다.
- 포맷은 임시 CLI [ktfmt](https://kotlin.github.io/ktfmt/) 0.64의 Kotlin 스타일로 정리했다. 프로젝트 의존성이나 사용자 IDE 설정에는 추가하지 않았다.

변환 후 전체 테스트 515개와 형식 정리 후 최종 clean build도 통과했다. CLI·서버 확인 결과는 [VALIDATION.md](VALIDATION.md)에 별도 기록한다.

## 검증 중 복원한 자동 변환 차이

- Boolean 프로퍼티의 이름 변경으로 생긴 JPQL 경로 차이를 원본 backing field명으로 복원했다.
- JPA Auditing이 갱신할 수 없는 `val` 시각을 `var`로 수정했다.
- Java의 숫자 변환을 Kotlin 숫자 변환 함수로 옮기고, 평점이 없는 장르 평균·리뷰·누락 JWT 클레임의 null 반환을 보존했다.
- IGDB 응답의 누락 가능한 목록을 nullable로 유지했다.
- 테스트 기대값을 고치지 않고 전환 코드를 수정했다. 테스트 준비 코드의 최소 변경은 README 표에서 확인할 수 있다.

- Kotlin 컴파일러의 로컬 캐시 `.kotlin/`은 Git에서 제외한다.
- 운영 코드 전환 커밋 메시지: `refactor: 백엔드 운영 코드를 Kotlin으로 전환`

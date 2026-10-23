# usergame 테스트 Kotlin 전환

usergame의 Java 테스트 9개를 동일 패키지의 `src/test/kotlin`으로 옮겼다.
운영 코드와 빌드 의존성은 변경하지 않고 JUnit 5, AssertJ, Mockito,
`@MySqlIntegrationTest` 구성을 유지했다.

## 전환 기준

- 운영 코드의 프로퍼티, `List`, `UserGame?` 조회 반환 계약을 사용한다.
- 저장된 엔티티의 필수 ID는 `checkNotNull`로 확인하고 선택적 입력은 nullable로 유지한다.
- 긴 `UserGameReqBody` 생성에는 named argument를 사용한다.
- JPA 연관 도메인이 아직 `MutableList`를 요구하는 경우 해당 테스트 데이터만 그 계약에 맞춘다.
- Mockito 매처와 captor는 null을 반환할 수 있다. non-null 인자에는 대체 값을 전달하되,
  매처 등록과 mock 호출 사이에서 다른 mock을 생성하지 않는다.
- `@MethodSource`는 `companion object`와 `@JvmStatic`으로 노출한다.
- 동시성 테스트는 트랜잭션 경계, 래치, 타임아웃, MySQL 중복 키 오류 검증과 정리를 유지한다.

## 검증

2026-10-23 기준 전환 전후 테스트 메서드 74개와 표시명을 유지했으며,
파라미터 확장 후 클래스별 실행 수도 동일하다.

| 테스트 | 실행 수 |
| --- | ---: |
| UserGameMembershipTest | 15 |
| UserGameConcurrencyTest | 2 |
| UserGameFavoriteMembershipTest | 10 |
| UserGameReviewStatisticsTest | 4 |
| UserGameSearchTest | 11 |
| UserGameServiceFavoriteTest | 5 |
| UserGameServiceLibraryTest | 33 |
| UserGameServiceProfileTest | 13 |
| UserGameServiceRecentTest | 2 |
| 합계 | 95 |

`back` 디렉터리에서 실행한다. MySQL 통합 테스트에는 Docker가 필요하다.

```bash
./gradlew clean test --tests 'com.gamelog.nbe121423355.domain.usergame.*'
```

Java 테스트 클래스가 남지 않도록 clean 빌드로 전환 결과를 확인했다.
최종 검증에서는 review·탈퇴 관련 테스트까지 함께 실행해 202개 모두 통과했고,
usergame 95개의 실패·오류·건너뛴 테스트는 모두 0개였다.

```bash
./gradlew test \
  --tests 'com.gamelog.nbe121423355.domain.usergame.*' \
  --tests 'com.gamelog.nbe121423355.domain.review.*' \
  --tests '*UserWithdrawTest'
```

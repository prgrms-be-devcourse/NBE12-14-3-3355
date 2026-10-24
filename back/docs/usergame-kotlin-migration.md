# usergame Kotlin 마이그레이션 회고

## 문서 목적

usergame 도메인을 Java에서 Kotlin으로 전환한 뒤, 단순 문법 변환을 넘어 Kotlin의 타입과 컬렉션 기능을 적용한 과정을 정리한다. 비교 기준은 운영 코드 전환 직전인 `3dd6334^`와 현재 코드다.

마이그레이션은 다음 단계로 진행됐다.

| 단계 | 기준 커밋 | 내용 |
| --- | --- | --- |
| 운영 코드 전환 | `3dd6334` | usergame을 포함한 백엔드 운영 Java 코드를 Kotlin으로 전환 |
| null 및 관용 표현 정리 | `3f32e9e`, `f6a7be6` | nullable 계약, DTO, Entity, Repository, Service를 Kotlin 방식으로 정리 |
| 테스트 전환과 API 검증 | `2b70038` | 기존 usergame Java 테스트를 Kotlin으로 전환하고 Controller·E2E·Repository 테스트 추가 |
| 후속 리팩터링 | `4aa00ba` | named argument, 테스트 helper, Kotlin 컬렉션 연산과 extension 적용 |

## 전환 범위

운영 코드에서는 기존 usergame Java 파일 25개가 같은 역할의 Kotlin 파일 25개로 전환됐다. 기존 Java 테스트 9개는 Kotlin으로 옮겼고, Controller·E2E·Repository 테스트를 추가해 현재 12개 테스트 클래스에서 109개 사례를 실행한다.

API 경로, JSON 필드, 데이터베이스 테이블과 컬럼, 라이브러리 포함 정책은 유지했다. Kotlin 타입을 정할 때는 DB와 HTTP 계약을 기준으로 삼았다.

## null을 컴파일 단계에서 구분

Java에서는 참조 타입만 보고 값의 필수 여부를 알 수 없었다.

```java
Optional<UserGame> findByUser_IdAndGame_Id(Long userId, Long gameId);
```

Kotlin에서는 조회 결과가 없을 수 있다는 사실을 반환 타입에 직접 표현한다.

```kotlin
fun findByUser_IdAndGame_Id(userId: Long, gameId: Long): UserGame?
```

서비스에서는 `Optional.map`, `orElseGet` 대신 safe call과 Elvis 연산자를 사용한다.

```kotlin
return userGameRepository
    .findByUser_IdAndGame_Id(userId, gameId)
    ?.let { userGame ->
        applyPlayRecord(userGame, reqBody, platform)
        UserGameSaveResult(userGame, false)
    }
    ?: run {
        val userGame = createUserGame(userId, gameId, reqBody, platform)
        UserGameSaveResult(userGame, true)
    }
```

요청 DTO도 실제 계약에 따라 구분했다.

- `isPlaying`, `isBacklog`, `isWishlist`, `isLiked`는 프런트가 항상 보내는 필수 `Boolean`이다.
- `playStatus`, 플랫폼, 플레이 시간과 날짜는 값이 없을 수 있으므로 nullable이다.
- 응답 DTO의 저장된 엔티티 ID는 `Long`으로 노출하며 변환 시 `checkNotNull`로 영속화 전제조건을 확인한다.
- JPA가 생성 전 ID를 갖지 않는다는 특성 때문에 Entity의 `id`는 `Long?`로 유지한다.

모든 Optional을 기계적으로 없애지는 않았다. `JpaRepository.findById()`는 Java API이므로 여전히 `Optional`을 반환하며, Mockito로 이 메서드를 stubbing하는 테스트도 `Optional`을 사용한다. 실제 Kotlin 테스트 조회에서는 `findByIdOrNull()`을 사용한다.

## DTO가 값 객체로 명확해짐

Java record로 표현하던 요청과 응답은 Kotlin `data class`로 전환했다. 값 비교, `hashCode`, `toString`, 구조 분해와 `copy`를 Kotlin 언어 기능으로 제공한다.

긴 생성자 호출에는 named argument를 사용한다.

```kotlin
UserGameDto(
    id = checkNotNull(userGame.id),
    userId = checkNotNull(userGame.user.id),
    gameId = checkNotNull(userGame.game.id),
    platformId = userGame.platform?.id,
    playStatus = userGame.playStatus,
    // ...
)
```

필드가 많은 usergame DTO와 Entity에서 각 값의 의미가 호출 위치에 드러나고, 인자 순서를 잘못 넣을 가능성이 줄었다. Bean Validation과 Swagger 애노테이션은 Kotlin 프로퍼티가 아니라 Java field에 적용되어야 하므로 `@field:` use-site target을 사용한다.

`UserGameReqBody`에는 테스트 편의를 위한 기본값이나 보조 생성자를 넣지 않았다. 누락된 Boolean을 `false`로 받아들이면 HTTP 계약이 느슨해지기 때문이다. 테스트에서는 `recordRequest()` fixture가 기본값을 채우고, 운영 DTO는 필수 입력 계약을 유지한다.

## Entity에서 상태 변경 경로를 유지

JPA Entity는 `data class`로 만들지 않았다. 프록시와 식별자 생명주기, 지연 로딩, 동등성 동작을 보존하기 위해 일반 `class`를 사용한다.

- JPA용 기본 생성자를 유지한다.
- 영속화 전에는 ID가 없으므로 `id: Long?`로 표현한다.
- 필수 연관관계인 `user`, `game`은 JPA 초기화와 애플리케이션 사용 시점을 함께 만족하도록 `lateinit`으로 표현한다.
- 외부에서는 상태를 임의로 대입하지 못하도록 setter를 제한하고 `changePlaying`, `changeWishlist` 같은 도메인 메서드로 변경한다.
- Boolean과 nullable 필드에는 실제 초기 상태를 기본값으로 둔다.
- 긴 생성자에는 기본 인자와 named argument를 사용하되, Java 호출부가 사용하는 2개 인자 생성자는 호환성을 위해 명시적으로 유지한다.

중복 구현도 줄였다.

```kotlin
fun clearPlayStatus() = changePlayStatus(null)

private fun hasPositiveHours(hours: BigDecimal?): Boolean =
    hours != null && hours.signum() > 0
```

각 상태 변경 뒤 `updateLibraryStatus()`를 호출하는 규칙은 기존 동작과 동일하게 유지했다.

## Java Stream을 Kotlin 컬렉션 연산으로 교체

초기 Java 코드는 `Stream`, `Collectors`, `IntStream`을 사용했다.

```java
Map<Long, Game> gameMap = userGames.stream()
        .collect(Collectors.toMap(
                ug -> ug.getGame().getId(),
                UserGame::getGame
        ));
```

현재 코드는 컬렉션의 목적을 직접 표현한다.

```kotlin
val gameMap = userGames.associate { userGame ->
    checkNotNull(userGame.game.id) to userGame.game
}
```

적용한 변환은 다음과 같다.

| 목적 | Java 방식 | Kotlin 방식 |
| --- | --- | --- |
| nullable 플레이 시간 제거 | `map` + null `filter` | `mapNotNull` |
| 합계 계산 | `reduce` | `fold` |
| ID와 Entity 매핑 | `Collectors.toMap` | `associate` |
| 순서가 있는 Entity 생성 | `IntStream.range` | `mapIndexed` |
| 조건별 개수 | `stream().filter().count()` | `count { }` |
| 장르 게임 수 합계 | `mapToLong().sum()` | `sumOf` |
| DTO 변환 | `stream().map().toList()` | `map(::Response)` |
| 중복 검증 | `stream().distinct().count()` | `distinct().size` |

이 변경으로 `UserGameService`에서 `java.util.stream`과 `java.util.function` 의존성이 사라졌다. 중간 컬렉션의 nullable 원소도 타입 추론으로 제거되어 후속 `requireNotNull`이 필요 없어졌다.

## scope function과 표현식 본문 사용

nullable 값으로 작업을 시작할 때 `let`을 사용한다.

```kotlin
private fun findPlatform(platformId: Long?): Platform? =
    platformId?.let {
        platformRepository.findById(it)
            .orElseThrow { ServiceException("404-4", "플랫폼을 찾을 수 없습니다.") }
    }
```

객체를 만들고 여러 프로퍼티를 설정하는 검색 요청과 mock fixture에는 `apply`를 사용한다. 단순 변환 함수는 표현식 본문으로 줄였다. 반대로 트랜잭션 흐름이나 상태 변경을 숨길 수 있는 곳에는 `also`와 `let`을 억지로 적용하지 않았다.

## 테스트 코드 변화

기존 Java 테스트의 시나리오와 기대값을 보존하면서 Kotlin으로 전환했다.

- 긴 요청 생성은 named argument와 `recordRequest()` fixture를 사용한다.
- 실제 Spring Data 조회는 `findByIdOrNull()`로 확인한다.
- 필요한 ID는 `checkNotNull`로 테스트 전제조건을 명시한다.
- 생성 후 수정하지 않는 작업 목록은 `MutableList` 대신 `List(threadCount) { ... }`로 만든다.
- 두 MySQL 테스트에 중복되던 예외 검증은 `Throwable.assertMySqlDuplicateEntry()` extension으로 통합했다.
- 반복 파라미터 테스트가 각 사례의 의도를 감추는 곳은 독립 테스트로 분리했다.
- Controller 테스트는 인증, 요청 validation, 응답 매핑과 상태 코드를 검증한다.
- E2E 테스트는 실제 로그인부터 HTTP 등록·조회·수정과 사용자 간 데이터 분리를 검증한다.

테스트 helper의 기본값은 운영 계약을 바꾸지 않는다. helper는 완전한 `UserGameReqBody`를 테스트 안에서 만들어 주지만, 실제 Controller 요청에서는 네 Boolean을 계속 필수로 받는다. `playStatus`는 nullable이며 프런트는 값이 없을 때 `null`을 명시해 보낸다.

## 개선 효과와 한계

개선된 점은 다음과 같다.

- nullable 여부가 메서드 서명에 드러나 잘못된 호출을 컴파일 단계에서 발견할 수 있다.
- 긴 생성자 호출에서 필드 이름이 보여 Boolean과 시간 값의 순서 실수를 줄였다.
- Java Stream용 타입 선언과 null 필터가 사라져 컬렉션 처리의 의도가 짧고 명확해졌다.
- Lombok에 의존하던 생성자와 getter가 Kotlin 언어 기능으로 대체됐다.
- 테스트 fixture와 extension으로 반복 코드를 줄이면서 운영 DTO 계약은 유지했다.
- Controller·E2E·MySQL 테스트를 함께 두어 단위 로직부터 실제 HTTP와 DB 제약까지 검증 범위를 넓혔다.

다음 특성은 의도적으로 남아 있다.

- JPA Entity ID는 생성 전 상태 때문에 nullable이다.
- `JpaRepository.findById()`와 이를 mock하는 코드는 Java `Optional` 경계를 따른다.
- JPA와 Java 테스트 호환에 필요한 기본 생성자와 2개 인자 생성자를 유지한다.
- 다른 도메인의 생성자가 `MutableList`를 요구하는 테스트 fixture는 해당 계약이 바뀌기 전까지 `mutableListOf()`를 사용한다.

## 검증 결과

2026-10-23 현재 다음 명령으로 usergame 전체 테스트를 실행했다.

```bash
cd back
./gradlew test --tests 'com.gamelog.nbe121423355.domain.usergame.*' --console=plain
```

| 항목 | 결과 |
| --- | ---: |
| 테스트 클래스 | 12 |
| 실행 테스트 | 109 |
| 실패 | 0 |
| 오류 | 0 |
| 건너뜀 | 0 |

세부 테스트 구성과 실행 방법은 [usergame-kotlin-tests.md](usergame-kotlin-tests.md), Controller·E2E 범위는 [usergame-api-tests.md](usergame-api-tests.md)에서 확인할 수 있다.

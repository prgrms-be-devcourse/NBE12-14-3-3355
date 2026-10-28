# 3차 프로젝트 발표 초안 — 문창원: 게임 데이터·검색

분석 기준: 2026-10-27, 현재 HEAD `ca2b858`. 실제 코드 수정 없이 Git 이력과 저장소의 검증·측정 기록을 대조했다. 아래 테스트 결과는 기존 실행 기록이며 이번 분석에서 재실행한 결과가 아니다.

## 슬라이드 1. 전환한 기능과 범위

**발표 문구**

> IGDB 게임 수집·동기화, 게임 검색·필터·정렬·자동완성, 한국어 번역 코드를 Kotlin으로 전환하고, 후속 리팩터링으로 null 처리와 데이터 변환 코드를 정리했습니다.

- 외부 API: `IgdbClient`, `DeepLClient`, `IgdbGameResponse`.
- 수집·동기화: `GameCollectionService`, `GameImportService`, `IgdbSyncService`, `IgdbSyncStore`, `IgdbSyncCatalog`, 상태·Repository 및 관리자 컨트롤러.
- 검색: `GameController`, `GameService`, `GameFilteredQueryRepository`, `GameSearchRequest`, `GameSort`, 목록 응답.
- 필터: `GameFilterController`, `GameFilterService`, `GameFilterResponse`.
- 번역·실행: `GameTranslationService`, `GameImportRunner`, `GameTranslationRunner`.
- 테스트: 담당 테스트 12개 클래스와 공통 준비 코드 1개, 총 13개 파일을 Kotlin으로 전환.

발표자 메모: 운영 전체 전환은 팀 공통 작업 `3dd6334`이며, 개인 후속 정리는 `1e3527c`, `3126694`, 테스트 전환은 `6b5c1cb`로 설명한다. 공용 Game 엔티티·Repository의 전체 변경이나 다른 담당자의 추천 기능 전체를 개인 실적으로 합산하지 않는다. CI·Prometheus·Grafana 설정은 Kotlin 소스 전환 건수에 포함하지 않는다.

## 슬라이드 2. 전환 효과 — 가독성과 안전성

**발표 문구**

> null 가능성을 타입에 표시하고, 입력 정규화 이후에는 non-null 값으로 처리했습니다. 컬렉션 변환과 객체 초기화에는 Kotlin 문법을 적용해 처리 의도를 더 직접적으로 표현했습니다.

- 안전성: 외부 응답의 누락 가능한 필드는 `String?`, `Long?`, nullable 목록으로 표현. 모든 필드를 무조건 non-null로 바꾸지 않았다.
- 입력 경계: `GameSearchRequest`는 nullable 페이지·크기를 받아 `page ?: 0`, `size ?: 20`으로 정규화한다. 내부 프로퍼티는 `Int`이므로 이후 불필요한 safe call을 제거할 수 있다. 음수 등 유효성 검사는 기존 validation을 유지한다.
- 재할당 제한: 수집의 `var game` 분기를 `val game = ...` 표현식으로 정리. `val`은 참조 재할당을 막으며 Game 객체 자체의 변경을 막는 것은 아니다.
- 가독성: `map(::GameListResponse)`, 컬렉션 `map`, `apply`, `also`, `try` 표현식을 사용.
- 외부 연동 보존: 번역의 원문 저장 → 외부 DeepL 호출 → 번역 결과 저장 순서와 트랜잭션 경계를 유지.

발표자 메모: 기존 Java DTO도 record였으므로 “Kotlin data class가 처음으로 getter·equals를 자동 생성해 대량 제거했다”는 설명은 맞지 않는다. `@JvmRecord data class`의 핵심은 기존 Java 호출 호환성과 Kotlin의 명시적 nullable 타입이다. null 관련 사고 감소율은 측정되지 않았다.

## 슬라이드 3. 불필요한 코드가 얼마나 줄었나

**발표 문구**

> 전체 파일 길이보다 중복 분기와 Java식 래퍼 제거에 집중했습니다. 수집 서비스의 후속 Kotlin 리팩터링에서는 115줄을 100줄로 줄이고, 관계 목록 3곳의 null 조기 반환을 제거했습니다.

같은 리팩터링 커밋 `3126694^ → 3126694`의 물리적 줄 수(빈 줄·주석·import 포함):

- `GameImportService`: **115 → 100줄**, 15줄·**13.0% 감소**.
- `GameService`: **98 → 94줄**, 4줄·**4.1% 감소**.
- `DeepLClient`: **64 → 63줄**, 1줄 감소.
- `GameTranslationService`: **113 → 117줄**, 4줄 증가. 명시적인 import와 호출 줄바꿈으로 가독성을 개선한 사례.
- 위 4개 파일 합계: **390 → 374줄**, 16줄·**4.1% 감소**.
- 수집 관계 처리의 `if (목록 == null) return` **3곳 → 0곳**: `orEmpty()`로 같은 건너뛰기 동작을 표현.
- 앞선 `1e3527c`에서는 GameService의 명시적 `java.util.function.Function` 래퍼 **2곳**, 명시적 `LongSupplier` 표기 **1곳**을 생성자 참조·람다로 정리. JVM에서 객체 할당이 사라졌다는 뜻은 아니다.

발표자 메모: “프로젝트 코드 13% 감소”가 아니라 “수집 서비스의 후속 리팩터링에서 13% 감소”라고 말한다. 줄 수에는 포맷 변화도 포함되며 삭제된 모든 줄을 불필요한 로직으로 볼 수 없다.

### 전체 감소율을 주장하지 않는 이유

담당 핵심 23개 파일을 고정해서 집계하면 Java 기준 `47997bf` **1,235줄**, 최초 Kotlin 전환 `3dd6334` **1,545줄**, 현재 **1,558줄**이다. 최초 전환은 약 **25.1% 증가**했다. 이는 전체 담당 파일의 완전한 목록이나 개인 변경량이 아니라 아래 명시한 표본이다.

Java record·Lombok으로 이미 간결했던 코드, Java 접근자·동등성 계약을 유지하는 호환 코드, Kotlin 포맷의 줄바꿈이 영향을 준다. 예를 들어 `GameSearchRequest`는 47→101줄이며 nullable 입력 정규화와 기존 접근자·equals/hashCode를 유지한다. 현재 코드에는 후속 기능·문서화 변경도 포함되므로 Java→현재 차이를 순수 언어 효과로 사용하지 않는다.

## 슬라이드 4. 코드 전후 비교 — null 분기와 컬렉션

### 사례 A: 누락된 관계 목록 처리

Java 기준 `47997bf`, `GameImportService`의 해당 부분:

```java
if (genres == null) {
    return;
}

for (IgdbGameResponse.NamedResource response : genres) {
    // 관계 저장
}
```

현재 Kotlin, 같은 처리 부분:

```kotlin
for (response in genres.orEmpty()) {
    // 관계 저장
}
```

**설명:** 장르·플랫폼·시리즈 3곳에서 null이면 저장하지 않는 계약을 유지하면서 조기 반환을 제거했다. private 함수 인자도 `MutableList`에서 `List`로 바꾸어 함수가 목록을 수정할 필요가 없음을 드러냈다. Kotlin `List`는 읽기 전용 인터페이스이며 원본의 절대적 불변성을 보장하지 않는다.

### 사례 B: 필터 응답 변환

Java 기준:

```java
genreRepository.findAll(Sort.by("name"))
    .stream()
    .map(genre -> new GameFilterResponse.Option(genre.getId(), genre.getName()))
    .toList();
```

현재 Kotlin:

```kotlin
genreRepository
    .findAll(Sort.by("name"))
    .map { genre -> Option(genre.id, genre.name) }
```

**설명:** 장르·플랫폼 목록 2곳에서 `stream()`·`toList()` 표기와 getter 호출을 줄였다. 목록 내용·정렬을 비교하는 사례이며 Java Stream.toList와 Kotlin map 결과의 수정 가능성까지 동일하다고 주장하지 않는다. 실행시간 개선 수치는 없다.

### 사례 C: 기존 게임 갱신 / 신규 게임 저장

현재 Kotlin의 실제 표현:

```kotlin
val existingGame = gameRepository.findByIgdbId(response.id).orElse(null)
val game = existingGame?.apply { updateFromIgdb(response) }
    ?: gameRepository.save(Game.createFromIgdb(response)).also { newGames.add(it) }
```

**설명:** 기존 게임이면 `apply`로 갱신하고, 없으면 Elvis 연산자의 오른쪽에서 저장한 뒤 `also`로 신규 목록에 추가한다. Java의 가변 지역 변수와 if/else 분기를 하나의 값 결정 흐름으로 표현한다. Repository의 Optional 자체는 이 부분에 남아 있다.

## 슬라이드 5. 추가 개선과 검증

**발표 문구**

> 추천 계산 완료 여부와 검색 가능 여부를 분리해, 추천 준비가 끝나지 않은 게임도 기존 검색 조건에 맞으면 검색·상세 조회할 수 있도록 개선했습니다. 수집 실패 후 재개와 중복 방지는 실제 MySQL을 사용하는 통합테스트로 보강했습니다.

- 기능 개선 `50a1886`: 검색·자동완성·COUNT 및 상세 접근에서 추천 계산 완료 조건을 제거. 추천 후보·인기 게임의 준비 상태 정책과 검색 노출 정책을 분리.
- 테스트 보강 `ea2c3f8`: 동기화 정상 완료·실패 후 재개 2개, 재수집 중복 방지·null 관계 보존 2개, 필터 빈 배열·이름순 응답 2개, **총 6개 시나리오 추가**.
- 테스트 Kotlin 전환 `6b5c1cb`: **12개 테스트 클래스 + 공통 준비 코드 1개**. 당시 기록상 **56개 실행 통과**, 메서드 51개와 assertion 진입점 231개 유지.
- 운영 검증 도구: k6의 검색 조건별 1/5/10/20 RPS 시험, 중단 기준, 결과 집계, Prometheus·Grafana 대시보드와 인덱스 비교 스크립트 추가.

발표자 메모: IGDB 동기화·검색·한국어 번역 자체는 Java 기준 코드에 이미 존재한다. 이를 3차 신규 기능이라고 소개하지 않는다. 확인된 내용은 기존 기능 확장과 검증·측정 체계 보강이다. 관리자 추천 갱신 전체는 다른 담당 영역이므로 개인 신규 기능으로 소개하지 않는다.

검증 범위: 외부 IGDB 응답만 대체하고 내부 서비스·Repository·MySQL을 연결한 테스트가 있다. 실제 외부 IGDB 통신이나 DeepL 호출까지 검증한 결과는 아니다. 기존 GameTranslationTest 삭제 기록이 있어 번역까지 전체 자동 검증했다고 발표하지 않는다.

## 슬라이드 6. 성능 개선 — 검색 정렬 인덱스

**발표 문구**

> 기본 검색의 정렬 순서에 맞춘 인덱스를 추가해 대량 후보 정렬을 줄였습니다. 28만 건의 로컬 데이터에서 `a` 첫 페이지의 목록 SQL 실행시간 중앙값이 139ms에서 0.178ms로 감소했습니다.

측정 조건: 2026-10-27, 로컬 MySQL 8.4, 게임 **284,294건**. 동일 SQL에서 후보 인덱스를 사용할 수 있는지 세션 설정으로 비교. 조건별 예열 1회 후 EXPLAIN ANALYZE 3회 중앙값. 페이지 크기 20, 출시일 1970-01-01 이상·2026-10-28 미만.

- `a`, page 0: **139 → 0.178ms**, **99.87% 감소**. 후보 인덱스 읽기 **284,294 → 26개**.
- `a`, page 100: **147 → 1.08ms**, **99.27% 감소**.
- `Baldur`, page 0: **112 → 57.6ms**, **48.57% 감소**.
- 결과 없는 검색: **101 → 109ms**, 약 **7.92% 증가**.

개선 원리: `COALESCE(igdb_rating, -1.0) DESC, id ASC`를 인덱스 선두에 두고 출시일·제목·원본 평점을 뒤에 배치. 기존 출시일 우선 인덱스도 유지한다. `a` 첫 페이지에서는 대량 후보 정렬이 사라지고 최종 20행 정렬만 남았다.

**슬라이드 각주에 반드시 포함:** “HTTP 응답시간이 아닌 목록 SQL의 EXPLAIN ANALYZE 중앙값. COUNT 제외. 로컬 순차 측정.”

발표자 메모:

- `a` 첫 페이지 COUNT는 **93.5 → 94.7ms**로 개선되지 않았다. 목록 SQL만 빨라졌으므로 API 전체가 99.87% 빨라졌다고 말할 수 없다.
- 네 조건에서 선택된 전체 행·순서·COUNT가 동일했음을 비교했다. 모든 필터·정렬·자동완성을 검증한 것은 아니다.
- 측정 순서는 무작위가 아니고 동시 부하 시험은 수행하지 않았다. 인덱스 저장 공간·쓰기 비용도 미측정이다.
- 기존 k6 결과는 추천 완료 조건 제거 전 기록이며 변경 후 재측정되지 않았다. 이 k6 p95를 인덱스 변경 후 SQL 시간과 짝지어 전후 개선율을 계산하면 안 된다.
- Kotlin 전환 자체의 런타임 성능 향상률은 미측정. 언어 전환과 DB 인덱스 개선을 별도 성과로 설명한다.

## 마무리 발표 문장

> Kotlin 전환에서는 null 가능성과 데이터 처리 의도를 코드에 명확히 표현하는 데 집중했습니다. 후속 리팩터링으로 중복 분기를 정리하고, 검색 노출 정책과 검증 체계를 보강했습니다. 성능은 별도의 인덱스 실험으로 확인했으며, 목록 조회 개선 이후에는 정확한 전체 개수 COUNT가 남은 병목임을 확인했습니다.

## 근거와 재현

- Java 기준: `47997bf99150482084e6849635065cb2d3692718`.
- 최초 전체 전환: `3dd6334`.
- 개인 후속 표현 정리: `1e3527c`, `3126694`.
- 테스트 보강·전환: `ea2c3f8`, `6b5c1cb`, `docs/game-data-search-testing.md`.
- 동작 보존의 기존 기록: `migration/IMPLEMENTATION.md`, `migration/VALIDATION.md`. 최초 전환 당시 전체 Java 테스트 515개 통과 및 공개 조회 API 7개 JSON 해시 일치. 개인 테스트 건수나 현재 HEAD의 재검증 결과가 아니다.
- 성능: `docs/search-score-index-results.md`, `docs/search-load-test-results.md`.
- 인덱스·실험: `monitoring/search-score-index.sql`, `k6/compare-score-index.ps1`.

줄 수 표본의 공통 경로는 Java `back/src/main/java/com/gamelog/nbe121423355/domain/game/`, Kotlin `back/src/main/kotlin/com/gamelog/nbe121423355/domain/game/`이며 확장자는 각각 java/kt다.

```text
file                                      Java  최초Kotlin  현재
client/DeepLClient                          49      64      63
client/IgdbClient                          136     173     173
controller/AdminIgdbSyncController          28      28      47
controller/GameController                  54      49      58
controller/GameFilterController            29      21      21
dto/GameFilterResponse                     18      17      17
dto/GameListResponse                       31      23      23
dto/GameSearchRequest                      47     101     101
dto/GameSort                                9       9       9
dto/IgdbGameResponse                        29      30      30
init/GameImportRunner                      30      27      27
init/GameTranslationRunner                 23      20      20
repository/GameFilteredQueryRepository     122     171     159
service/GameCollectionService              16      12      12
service/GameFilterService                  33      34      30
service/GameImportService                 123     107     100
service/GameService                        99     106      94
service/GameTranslationService             78     114     117
sync/IgdbSyncCatalog                       115     243     261
sync/IgdbSyncRepository                     11      12      12
sync/IgdbSyncService                        42      48      48
sync/IgdbSyncState                          29      45      45
sync/IgdbSyncStore                          84      91      91
합계                                     1235    1545    1558
```

집계 방법: PowerShell에서 `@(git show '<커밋>:<파일경로>').Count`. 빈 줄·주석·import를 포함하며 마지막 개행 유무와 무관하게 텍스트 줄을 센다. 감소율은 `(이전-이후)/이전*100`이다.

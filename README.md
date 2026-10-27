<div align="center">
  <h1>
    <img src="front/public/gamelog-logo-console.png" alt="GameLog Logo" width="54" align="absmiddle" />
    GameLog.
  </h1>
  <br>
  <h3>플레이를 기록하고, 다음에 빠져들 게임을 발견하세요</h3>
  <p>게임 탐색부터 플레이 기록, 리뷰, 취향 기반 추천과 커뮤니티까지 연결하는 게임 기록 플랫폼</p>
</div>

---

## 📝 프로젝트 소개

**GameLog**는 즐긴 게임과 앞으로 플레이할 게임을 한곳에 모으고, 나의 기록을 바탕으로 새로운 게임을 발견하는 서비스입니다.

장르와 플랫폼으로 게임을 탐색하고, 플레이 상태·시간·평점·리뷰를 기록할 수 있습니다. 위시리스트와 백로그로 다음 플레이를 준비하고, 프로필에서 게임 기록과 취향 통계를 확인합니다.

처음 방문한 사용자는 온보딩에서 선호 장르와 게임을 선택해 추천을 받습니다. 플레이 기록이 쌓이면 실제 기록을 우선 활용해 개인화된 게임을 추천합니다.

> 프로그래머스 백엔드 데브코스 3차 팀 프로젝트 · GameLog 고도화 및 Kotlin 전환
> 2차 프로젝트의 서비스를 이어서 개선하며, 저장소·패키지 이름은 기존 `NBE12-14-2-3355`를 유지합니다.

- **서비스:** [GameLog](https://nbe-12-14-2-3355.vercel.app/)
- **3차 개발 기간:** 2026.10.02 ~ 진행 중 (2026.10.27 기준)
- **2차 개발 기간:** 2026.09.01 ~ 2026.09.30
- **인원:** 5명 (팀 삼삼오오)

| 팀원 | 담당 도메인 | 주요 작업 |
|---|---|---|
| 유시헌 | 회원 · 인증 · 보안 | JWT 인증·자동 재발급, 프로필·비밀번호 변경, 관리자 승격, 배포 환경, Sentry |
| 문창원 | 게임 데이터 · 검색 | IGDB 수집·동기화, 게임 검색·필터·정렬, 한국어 번역, CI, Prometheus·Grafana |
| 김기백 | 게임 상세 · 추천 | 게임 상세·통계, 연관·맞춤 추천, 메인 화면, 팔로우, 추천 쿼리 최적화 |
| 송혜민 | 라이브러리 · 프로필 | 라이브러리 등록·상태 관리, 마이 프로필 탭(기록 산점도·장르 분포), 타 사용자 프로필 |
| 이제혁 | 리뷰 | 리뷰 CRUD·작성 UI, 리뷰 좋아요, Swagger API 문서화 |

---

## 🚀 3차 프로젝트 개선 사항

2026.10.02~2026.10.27의 현재 브랜치 커밋과 구현을 기준으로 정리했습니다. 해당 기간에 조회되는 첫 커밋은 10월 16일입니다.

| 영역 | 주요 변경 | 관련 기록 |
|---|---|---|
| Kotlin 전환 | 백엔드 운영 코드 전체 전환, null 타입·컬렉션·복합키와 도메인 테스트 정리 | `3dd6334`, `f6a7be6`, `1b4f215` · [전환 기록](migration/README.md) |
| 검색 성능 | 페이지 우선 조회, 필터·정렬 쿼리와 인덱스 개선, k6 단계별·동시 사용자 시험 | `c175967`, `4771998`, `a7ec8ed`, `4d7a853` · [시험 안내](k6/README.md) |
| 추천 운영 | 연관 후보 사전 계산, 관리자 비동기 갱신·실패 이력, 변경 영향 게임 부분 갱신과 장르 조합별 계산 재사용 | `690dd52`, `71f5e3c`, `15f77ef`, `8c43737` |
| 커뮤니티·알림 | 커뮤니티 리뷰·댓글·답글, 자유게시판, 활동 알림과 읽음·보관 정책 | `08cf6e3`, `d91df62`, `a206c9d` |
| 인증·기록 정합성 | Refresh Token Rotation·재사용 감지, 회원 탈퇴, 기록·리뷰 통합 트랜잭션과 버전 충돌 처리 | `46ca736`, `34c57d5`, `88486d1`, `47997bf` |
| 테스트·API 구조 | H2 제거 및 MySQL Testcontainers 통일, API·E2E·동시성 검증, 프로필 서비스·컨트롤러 분리, Swagger 명세 보완 | `b24dfbd`, `78c9750`, `e550104`, `410fd3d` |

## ✨ 주요 기능 (Key Features)

### 1. 게임 탐색 · 검색

IGDB에서 수집한 게임 정보를 바탕으로 제목 검색, 검색어 추천, 장르·플랫폼 필터와 정렬을 제공합니다. 게임 상세 화면에서는 게임 소개와 장르·플랫폼, 평점·플레이 시간 통계, 연관 게임을 확인할 수 있습니다.

### 2. 나만의 게임 라이브러리

게임별 플레이 상태와 시간을 기록하고, 플레이 중 여부·위시리스트·백로그·좋아요를 관리합니다. 플레이 상태는 `PLAYED`, `COMPLETED`, `RETIRED`, `SHELVED`, `DROPPED`로 구분하며, 라이브러리에서 조건에 맞는 기록을 찾아볼 수 있습니다.

### 3. 리뷰 · 평점 · 좋아요

게임에 평점과 리뷰를 남기고 수정·삭제할 수 있습니다. 평점은 0.5 단위로 검증하며, 스포일러 여부를 표시할 수 있습니다. 다른 사용자의 리뷰에 좋아요를 남기거나 부적절한 리뷰를 신고할 수 있고, 관리자는 신고 목록과 처리 상태를 관리합니다.

### 4. 취향 기반 맞춤 추천

플레이 기록과 선호 장르, 연관 게임 정보, IGDB 평점을 조합해 최대 5개의 게임을 추천합니다. 추천에 사용할 플레이 기록이 없으면 온보딩에서 선택한 선호 게임·장르를 활용하며, 이미 기록한 게임은 후보에서 제외합니다.

### 5. 프로필 · 취향 통계

닉네임과 프로필 이미지를 수정하고, 좋아하는 대표 게임을 설정할 수 있습니다. 프로필에서는 라이브러리와 최근 기록, 평점·플레이 시간 산점도, 장르 분포와 게임 취향 통계를 확인합니다. 인생 게임은 내 라이브러리에 포함된 게임 중 최대 5개를 선택하며, 공개 프로필 조회와 내 프로필 조회 API를 분리했습니다.

### 6. 회원가입 · 로그인 · 온보딩

이메일 기반 회원가입·로그인과 이메일·닉네임 중복 확인을 지원합니다. 가입 후 선호 장르와 게임을 선택하거나 온보딩을 건너뛸 수 있습니다. 인증은 JWT Access Token과 Refresh Token을 사용하며, Refresh Token 교체와 재사용 감지를 지원합니다. 설정 화면에서 회원 탈퇴할 수 있고, 탈퇴한 사용자의 남은 Access Token으로 내 정보에 접근하는 것도 차단합니다.

### 7. 커뮤니티 · 자유게시판

커뮤니티에서 리뷰를 탐색하고 댓글·답글과 댓글 좋아요로 소통합니다. 자유게시판에서는 글 작성·수정·삭제, 좋아요, 댓글·답글과 신고를 지원하며, 관리자는 게시글·댓글 신고를 처리합니다.

### 8. 사용자 활동 알림

팔로우, 리뷰 좋아요·댓글·답글·댓글 좋아요, 리뷰 숨김 처리와 자유게시판 댓글·답글 알림을 제공합니다. 커서 기반 목록 조회, 미확인 개수, 개별·전체 읽음 처리를 지원하며 일반 알림은 읽음 처리 후 7일 또는 미확인 상태로 생성 후 30일, 리뷰 숨김 안내는 읽음 여부와 관계없이 생성 후 90일이 지나면 정리합니다.

---

## 🔍 기술 하이라이트 (Tech Highlights)

### Kotlin 전환과 MySQL 기반 검증

백엔드 운영 코드를 Java에서 **Kotlin 2.4.20**으로 전환했습니다. nullable 타입, Kotlin 표준 컬렉션 함수, 확장 함수와 복합키를 정리하고, 프로필 조회 책임을 `UserGameProfileService`와 `UserGameProfileController`로 분리했습니다. 테스트도 도메인별로 Kotlin으로 전환했으며 일부 Java 테스트는 유지합니다.

H2 의존성과 설정을 제거하고 DB 통합 테스트를 **Testcontainers MySQL 8.4**로 통일했습니다. 서비스·저장소·API·E2E 테스트와 동시성·롤백 검증을 통해 전환 후 동작을 확인합니다. [MySQL 테스트 안내](back/docs/mysql-tests.md)에서 실행 조건을 확인할 수 있습니다.

### 연관 추천 사전 계산과 변경 영향 기반 갱신

기준 게임별 연관 후보를 최대 20개 저장해 상세 연관 추천과 맞춤 추천에서 재사용할 수 있습니다. 관리자 API는 비동기 실행 접수, 진행·실패 이력 조회와 재실행을 지원합니다. 추천 입력 변경의 영향 게임을 추적해 부분 갱신하며, 같은 장르 조합의 계산을 재사용합니다.

사전 계산 사용 여부는 `RECOMMENDATION_RELATED_PRECOMPUTED_ENABLED`와 `RECOMMENDATION_PERSONALIZED_PRECOMPUTED_ENABLED`로 각각 제어하며 기본값은 `false`입니다. 기능 활성화 전 후보 데이터 준비가 필요합니다.

### 기록·리뷰 저장과 동시 수정 정합성

게임 기록과 리뷰 저장을 하나의 트랜잭션으로 묶고, 리뷰의 `@Version`과 요청 버전을 검증해 오래된 화면의 수정·삭제가 최신 기록을 덮어쓰지 않도록 처리합니다. 신고 접수·관리자 처리의 동시성 제어와 탈퇴 시 리뷰 버전 갱신도 보강했습니다.

### 기록 기반 추천과 온보딩 추천의 연결

`PersonalizedGameRecommendationService`는 추천에 사용할 수 있는 사용자 기록을 먼저 확인합니다. 기록 기반 추천에서는 연관 게임의 순위 점수와 상위 선호 장르의 가산점을 계산하고, IGDB 평점의 50%를 더해 최종 점수를 만듭니다.

추천에 사용할 기록이 없으면 온보딩 선호 게임을 우선 활용하고, 선호 게임도 없으면 선호 장르로 후보를 찾습니다. 후보 정보는 일괄 조회하며, 최종 점수·사용자 평균 평점·출시일·게임 ID 순으로 정렬해 최대 5개를 반환합니다.

### Next.js API 프록시와 JWT 인증

브라우저는 Next.js의 `/api/*` 경로로 요청하고, Route Handler가 Spring Boot의 `/api/v1/*`로 전달합니다. 백엔드 주소는 서버 환경변수 `BACKEND_URL`로 설정합니다.

Access Token은 `Authorization: Bearer` 헤더로 전달하고, Refresh Token은 HttpOnly 쿠키로 관리합니다. 프록시는 백엔드의 `Set-Cookie`와 `New-Access-Token` 응답 헤더를 전달해 인증 흐름을 연결합니다.

### IGDB 데이터 수집과 한국어 설명 번역

IGDB에서 받은 게임·장르·플랫폼·시리즈 정보를 관계형 데이터로 저장합니다. 기존 게임은 IGDB ID로 찾아 갱신하고, 연결 데이터는 존재 여부를 확인해 중복 저장을 방지합니다. 외부 API 응답을 받은 뒤 저장 트랜잭션을 시작합니다.

한국어 설명 번역에는 DeepL을 사용합니다. 원문과 SHA-256 해시를 보관해 같은 원문의 중복 번역을 건너뛰며, 외부 번역 요청 동안 DB 트랜잭션을 유지하지 않습니다. 실행당 번역 개수를 제한하고 API 잔여 한도를 확인합니다.

### Cloudinary 이미지 업로드

이미지는 백엔드에서 Cloudinary로 업로드하고 반환된 HTTPS URL을 사용합니다. 빈 파일과 `image/*`가 아닌 MIME 타입을 거부하며, 파일 및 요청 크기는 각각 최대 5MB로 설정되어 있습니다.

### 실행 환경 분리와 자동 빌드

프론트엔드는 **Vercel**, 백엔드와 MySQL은 **Railway**에 배포합니다. 로컬은 Docker MySQL, 테스트는 Testcontainers MySQL 8.4를 사용합니다. GitHub Actions는 push와 pull request 이벤트마다 Java 25 환경에서 백엔드 빌드와 테스트를 실행합니다.

---

## 🧰 기술 스택 (Tech Stack)

### Backend

![Kotlin](https://img.shields.io/badge/Kotlin_2.4.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![JDK](https://img.shields.io/badge/JDK_25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger_UI-85EA2D?style=flat-square&logo=swagger&logoColor=black)

### Frontend

![Next.js](https://img.shields.io/badge/Next.js_16.3.5-000000?style=flat-square&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React_19.2.8-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript_5-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS_4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)

Next.js App Router와 React Context를 사용하며, 화면 스타일은 CSS Modules와 전역 CSS로 구성합니다.

### Database · Tools · External APIs

![Vercel](https://img.shields.io/badge/Vercel-000000?style=flat-square&logo=vercel&logoColor=white)
![Railway](https://img.shields.io/badge/Railway-0B0D0E?style=flat-square&logo=railway&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8.4-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit-25A162?style=flat-square&logo=junit5&logoColor=white)
![Cloudinary](https://img.shields.io/badge/Cloudinary-3448C5?style=flat-square&logo=cloudinary&logoColor=white)

- **Testcontainers**: 테스트용 MySQL 컨테이너
- **IGDB**: 게임 메타데이터 수집
- **DeepL**: 게임 설명 한국어 번역
- **Gradle**: 백엔드 빌드 및 테스트
- **ESLint**: 프론트엔드 정적 검사
- **k6 · Actuator · Prometheus · Grafana**: 부하 테스트 및 서버 지표 관찰
- **Sentry**: 서버 오류 수집 (`SENTRY_DSN` 설정 시 활성화)

---

## 🏗 시스템 구성 (Architecture)

![GameLog 시스템 구성도 — Vercel 프론트엔드, Railway 백엔드·MySQL, 외부 서비스 연동](docs/architecture.png)

브라우저의 서비스 요청은 **Vercel의 Next.js API 프록시**를 거쳐 **Railway의 Spring Boot API**에 전달됩니다. 백엔드는 Railway MySQL에 데이터를 저장하고 Cloudinary로 이미지를 업로드합니다.

위 구성도는 기존 배포 구조를 나타내며, 3차의 Kotlin 전환·추천 사전 계산·커뮤니티·알림 상세 구성은 본문을 참고하세요.

이미지 하단은 로컬 데이터 준비 과정입니다. IGDB 수집과 DeepL 번역은 `local` 프로파일에서 각각의 실행 옵션을 활성화했을 때 시작 시 수행합니다.

---

## 🗂 데이터베이스 구조 (ERD)

[![GameLog ERD](docs/images/gamelog-erd.png)](docs/images/gamelog-erd.png)

3차에 추가한 알림, 리뷰 댓글, 자유게시판 및 추천 갱신 이력·변경 추적의 상세 스키마는 [docs/sql](docs/sql)을 함께 확인하세요.


---

## 🛠 시작하기 (Getting Started)

### 1. 준비 사항

- JDK 25
- 프로젝트의 Next.js 버전을 지원하는 Node.js 및 npm
- Docker와 Docker Compose
- IGDB Client ID / Client Secret
- Cloudinary Cloud Name / API Key / API Secret
- 한국어 설명 번역을 실행하는 경우 DeepL API Key

이하 명령은 **저장소 루트에서 시작하는 Windows PowerShell 기준**입니다. macOS/Linux에서는 환경변수 설정에 `export`를 사용하고, `./gradlew.bat` 대신 `./gradlew`를 실행하세요.

### 2. MySQL 실행

```powershell
docker compose up -d mysql
docker compose ps
```

MySQL이 `healthy` 상태가 된 후 백엔드를 실행합니다. 로컬 기본 접속은 `localhost:3306`, 데이터베이스는 `gamelog`이며 계정 설정은 [docker-compose.yml](docker-compose.yml)에서 확인할 수 있습니다. 현재 `local` 프로파일의 기본 DB 이름은 `gamelog_retry`이므로, Compose가 생성하는 `gamelog`에 연결하려면 아래 실행 예처럼 데이터소스 URL을 지정하세요. 데이터는 `mysql-data` 볼륨에 보관됩니다.

### 3. 백엔드 환경변수 설정 및 실행

백엔드를 실행할 PowerShell에서 다음 값을 설정합니다. 아래 값은 실제 발급받은 정보로 교체하세요.

```powershell
$env:IGDB_CLIENT_ID = "your-igdb-client-id"
$env:IGDB_CLIENT_SECRET = "your-igdb-client-secret"
$env:CLOUDINARY_CLOUD_NAME = "your-cloud-name"
$env:CLOUDINARY_API_KEY = "your-cloudinary-api-key"
$env:CLOUDINARY_API_SECRET = "your-cloudinary-api-secret"

cd back
./gradlew.bat bootRun --args="--spring.datasource.url=jdbc:mysql://localhost:3306/gamelog?serverTimezone=UTC&characterEncoding=UTF-8"
```

기본 프로파일은 `local`입니다. 아래 수집·번역 실행도 Compose의 `gamelog`를 사용한다면 같은 `--spring.datasource.url` 인자를 함께 지정하세요. 로컬 DB 계정을 변경했다면 `DB_USERNAME`, `DB_PASSWORD`도 설정하세요. 백엔드는 기본적으로 `.env` 파일을 자동으로 읽지 않으므로 셸 또는 IDE의 실행 환경변수로 주입합니다.

**처음 게임 데이터를 수집할 때**는 일반 실행 대신 다음 명령을 사용합니다.

관리자 계정은 상단 **게임 데이터 관리**(`/admin/igdb`)에서도 실행할 수 있습니다.
최초 전체 동기화 이후에는 변경분과 새로 출시된 게임만 수집합니다.
출시일은 1970년 1월 1일부터 실행 시점까지로 제한하며, 범위 밖으로 변경된 게임과 연결된 사용자 기록은 삭제합니다.
진행 상태·실패 후 재개·백업 정보는 [IGDB 동기화 안내](back/docs/igdb-sync.md)를 참고하세요.

```powershell
# back 디렉터리에서 실행
./gradlew.bat bootRun --args="--igdb.import.enabled=true"
```

기본 실행에서는 IGDB 자동 수집이 비활성화되어 있으므로, 비어 있는 DB에는 게임 목록이 표시되지 않습니다. 수집이 끝난 뒤에는 일반 실행 명령을 사용하면 됩니다.

<details>
<summary>선택: 게임 설명 한국어 번역</summary>

게임 데이터가 저장된 상태에서 DeepL 키와 실행 옵션을 설정합니다.

```powershell
$env:DEEPL_API_KEY = "your-deepl-api-key"
./gradlew.bat bootRun --args="--game.translation.enabled=true --game.translation.max-games=10"
```

번역은 기본적으로 비활성화되어 있으며, 위 명령은 한 번 실행할 때 최대 10개의 설명을 번역합니다.

</details>

### 4. 프론트엔드 실행

새 터미널을 열고 저장소 루트에서 실행합니다.

```powershell
cd front
Copy-Item .env.example .env.local
npm ci
npm run dev
```

`.env.local`의 `BACKEND_URL` 기본값은 `http://localhost:8080`입니다. 값을 바꾸면 프론트엔드 서버를 재시작하세요.

- **서비스**: [http://localhost:3000](http://localhost:3000)
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### 5. 테스트 및 빌드

백엔드는 `back` 디렉터리에서 실행합니다. DB를 사용하는 테스트는 `@MySqlIntegrationTest`로 Testcontainers의 MySQL 8.4에 연결합니다. 테스트 실행에는 Docker가 필요합니다.

게임 수집·검색 테스트의 보완 내용, Mockito 사용 경계 및 실행 결과는 [게임 수집·검색 테스트 보완 기록](docs/game-data-search-testing.md)을 참고하세요.

도메인별 서비스·컨트롤러 테스트와 동시성 테스트(같은 이메일·닉네임 동시 가입, 같은 게임 동시 등록 시 한 건만 저장)를 포함하며, push와 PR마다 GitHub Actions에서 실행됩니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "test"
./gradlew.bat build
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

테스트 결과는 `back/build/reports/tests/test/index.html`에서 확인할 수 있습니다. 테스트 후 환경변수를 해제해야 같은 터미널에서 다시 실행할 때 기본 `local` 프로파일을 사용할 수 있습니다.

프론트엔드는 `front` 디렉터리에서 실행합니다.

```powershell
npm run lint
npm run build
```

---

## ⚙️ CI · 운영 설정

### 배포 환경

- **Vercel — Frontend**: `front`의 Next.js 애플리케이션을 배포합니다. 서버 환경변수 `BACKEND_URL`에 Railway 백엔드의 HTTPS 주소를 설정해 API 요청을 전달합니다.
- **Railway — Backend**: `back`의 Spring Boot 애플리케이션을 배포하며, `SPRING_PROFILES_ACTIVE=prod`로 운영 설정을 사용합니다.
- **Railway — Database**: MySQL을 운영하며, 백엔드는 아래 운영 프로파일의 DB 환경변수로 연결합니다.

### GitHub Actions

[Backend CI](.github/workflows/ci.yml)는 push와 pull request 이벤트에 실행됩니다.

1. 저장소 체크아웃
2. Temurin JDK 25 설치
3. `SPRING_PROFILES_ACTIVE=test` 설정
4. `back` 디렉터리에서 `bash ./gradlew build --no-daemon` 실행

현재 저장소의 워크플로우는 백엔드 빌드·테스트를 담당합니다. 자동 배포, 프론트엔드 검사, 커버리지 게이트는 이 워크플로우에 포함되어 있지 않습니다.

### 부하 테스트 · 모니터링

k6로 부하를 주고, Actuator가 공개한 서버 지표와 k6 결과를 Prometheus로 모아 Grafana에서 확인했습니다. 로컬 모니터링 환경은 `docker compose -f compose.monitoring.yml up -d`로 실행합니다(Prometheus `:9090`, Grafana `:3001`).

**기존 추천 쿼리 개선 전후 기록** (로컬 환경, 가상 사용자 최대 10명, 에러율 0%)

| 지표 | 맞춤 추천 | 게임 상세 연관 추천 |
|---|---|---|
| p95 응답 시간 | 9.1s → **1.4s** | 약 2.9s → **약 1.4s** |
| p99 응답 시간 | 10.4s → **1.6s** | 약 2.9s → **약 1.5s** |
| 처리량 (RPS) | 1.1 → **4.0** | 약 2.5 → **약 3.9** |

| | 개선 전 | 개선 후 |
|---|---|---|
| 맞춤 추천 | ![맞춤 추천 개선 전 Grafana](docs/images/grafana-recommend-before.png) | ![맞춤 추천 개선 후 Grafana](docs/images/grafana-recommend-after.png) |
| 게임 상세 연관 추천 | ![연관 추천 개선 전 Grafana](docs/images/grafana-related-before.png) | ![연관 추천 개선 후 Grafana](docs/images/grafana-related-after.png) |

기준 게임 여러 개의 연관 게임을 쿼리 한 번으로 일괄 조회하고, `JOIN_FIXED_ORDER` 힌트로 IGDB 평점 필터를 먼저 적용했습니다. 로컬 PC에서 측정한 값이라 개선 전후 비교용입니다. 이 수치는 아래 3차 사전 계산·고정 VU 시험의 최신 결과를 의미하지 않습니다.

**3차 검색·추천 검증**

- 검색은 1/5/10/20 RPS 단계별 시험과 동시 사용자 시험을 제공합니다. [검색 시험 안내](k6/README.md), [계획](docs/search-load-test-plan.md), [측정 결과](docs/search-load-test-results.md)를 참고하세요. 결과 문서는 추천 계산 완료 조건 제거 전 측정으로, 변경 후 부하 성능은 재측정하지 않았습니다.
- 10월 27일 로컬 MySQL 284,294게임의 평점·ID 정렬 인덱스 실험에서 `a`, page=0 목록 SQL 중앙값은 139ms → 0.178ms였습니다. COUNT는 93.5ms → 94.7ms로 남았으며 HTTP·동시 부하 결과와는 다릅니다. [측정 조건과 한계](docs/search-score-index-results.md)를 함께 확인하세요.
- 추천은 맞춤·연관 API를 분리하고 고정 10·100 VU 시나리오를 추가했습니다. 실행·인증·지표 설정은 [추천 k6 안내](k6/recommendations/README.md)를 참고하세요.

### 운영 프로파일

`prod`는 Railway MySQL 환경변수(`MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`)를 사용합니다. `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`와 외부 API 환경변수도 설정해야 합니다.

운영 DB는 `ddl-auto: validate`와 SQL 초기화 비활성화 설정을 사용하므로 실행 전에 스키마를 준비해야 합니다. 3차의 리뷰 버전, 알림·댓글·자유게시판, 추천 실행 이력·변경 추적 및 검색 인덱스는 [docs/sql](docs/sql)과 [monitoring](monitoring)의 SQL을 확인해 기존 스키마와 적용 이력에 맞춰 준비합니다. 운영 Refresh Token 쿠키에는 `Secure`와 `SameSite=Lax`가 적용됩니다.

---

## 📂 프로젝트 구조 (Project Structure)

```text
NBE12-14-2-3355/
├── .github/workflows/          # 백엔드 CI
├── docs/                      # 시스템 구성도, ERD, SQL 및 성능 측정 기록
├── migration/                 # 3차 Kotlin 전환 계획·기준선·검증 기록
├── k6/                        # 검색·추천 부하 테스트 실행기
├── monitoring/                # Prometheus·Grafana 설정과 검색 인덱스 SQL
├── back/
│   ├── src/main/kotlin/com/gamelog/nbe121423355/
│   │   ├── domain/
│   │   │   ├── game/          # 게임 탐색, 수집, 번역, 추천
│   │   │   ├── user/          # 인증, 프로필, 온보딩 취향
│   │   │   ├── usergame/      # 라이브러리, 플레이 기록, 통계
│   │   │   ├── review/        # 리뷰, 평점, 좋아요, 신고, 댓글·답글
│   │   │   ├── board/         # 커뮤니티 자유게시판, 댓글, 신고
│   │   │   └── notification/  # 활동 알림, 읽음·보관 정책
│   │   └── global/            # 보안, 공통 응답, 예외 처리, 업로드
│   ├── src/main/resources/    # 프로파일별 애플리케이션 설정
│   ├── src/test/              # 서비스·컨트롤러·저장소 등 테스트
│   └── build.gradle.kts
├── front/
│   ├── public/                # 로고 및 정적 파일
│   ├── src/
│   │   ├── app/               # App Router 페이지와 API 프록시
│   │   ├── components/        # 게임 탐색·상세·기록·리뷰 UI
│   │   ├── features/          # 인증·프로필·리뷰·추천 API 및 상태
│   │   └── lib/               # API 유틸리티 및 프록시 공통 처리
│   └── package.json
└── docker-compose.yml         # 로컬 MySQL
```

<!-- 공개 전 추가할 자료: 주요 화면 캡처 -->

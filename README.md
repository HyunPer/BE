# Pleos Backend

현대차 Pleos 차량용 앱 공모전 백엔드 서버입니다.
폰 앱에서 만든 프로필(디스플레이·시트·공조·라이트·웰컴 사운드)을 저장하고, 차량 앱이 받아서 적용합니다.

기준 문서: [Notion 공모전 페이지](https://app.notion.com/p/Pleos-4044c75bb8a781d4aa5ecb3bfcca8074) · 설계: [docs/DESIGN.md](docs/DESIGN.md)

## 스택

| 항목 | 버전 |
| --- | --- |
| Kotlin | 2.3.21 |
| Spring Boot | 4.1.2 (Web MVC, Data JPA, Validation, Actuator) |
| API 문서 | springdoc-openapi 3.1.1 (Swagger UI) |
| JDK | 21 |
| Gradle | 9.8.2 (Wrapper 포함, 별도 설치 불필요) |
| DB | H2 파일 DB (로컬 개발용, `data/` 폴더) |

## 실행

JDK 21이 `JAVA_HOME`에 잡혀 있으면 됩니다.

```bash
./gradlew bootRun
```

- 기본 포트는 `8090`입니다. 다른 포트는 `PORT` 환경변수로 지정합니다.
- 헬스 체크: `http://localhost:8090/actuator/health`
- **API 문서(Swagger UI): `http://localhost:8090/swagger-ui.html`**. 폰 앱·차량 앱 그룹으로 나뉘어 있고, Authorize에 토큰을 넣으면 바로 호출할 수 있습니다.
- DB 콘솔: `http://localhost:8090/h2-console` (JDBC URL `jdbc:h2:file:./data/pleos`, 사용자 `sa`, 비밀번호 없음)
- Android 에뮬레이터에서는 호스트 PC를 `http://10.0.2.2:8090`으로 접근합니다.

## 테스트

```bash
./gradlew test
```

## API 개요

인증은 모두 `Authorization: Bearer <토큰>` 헤더로 합니다. 토큰이 없거나 틀리면 401입니다.

| 클라이언트 | Base path | 토큰 | 토큰 받는 곳 |
| --- | --- | --- | --- |
| 폰 앱 | `/api/v1` | 사용자 토큰 | `POST /api/v1/users` 응답의 `accessToken` |
| 차량 앱 | `/vehicle-api/v1` | 차량 토큰 | `POST /vehicle-api/v1/vehicles` 응답의 `vehicleToken` |

### 폰 앱

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/users` | 가입 (닉네임만, 토큰 불필요) | 201 |
| GET | `/users/me` | 내 정보 | 200 |
| GET | `/profiles` | 내 프로필 목록 (생성 순) | 200 |
| GET | `/profiles/{id}` | 프로필 조회 | 200 |
| POST | `/profiles` | 프로필 생성 | 201 + `Location` |
| PUT | `/profiles/{id}` | 프로필 전체 교체 | 200 |
| DELETE | `/profiles/{id}` | 프로필 삭제 | 204 |
| POST | `/vehicles/pair` | 차량 화면의 6자리 코드로 페어링 (첫 사람 OWNER) | 200 |
| GET | `/vehicles` | 내 차량 목록 | 200 |
| GET | `/vehicles/{vehicleId}` | 차량 상세 + 멤버 | 200 |
| PUT | `/vehicles/{vehicleId}/next-driver` | 다음 운전자 예약 `{profileId}` | 200 |
| GET | `/vehicles/{vehicleId}/next-driver` | 현재 대기 예약 (없으면 204) | 200/204 |
| DELETE | `/vehicles/{vehicleId}/next-driver` | 대기 예약 취소 | 204 |

### 차량 앱

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/vehicles` | 차량 등록 (첫 실행 1회, 토큰 불필요) | 201 |
| GET | `/me` | 내 차량 정보 (페어링 코드·멤버 수) | 200 |
| GET | `/me/profiles` | 멤버별 프로필 (시동 직후 1탭 선택 화면용) | 200 |
| GET | `/me/next-driver` | 시동 시 다음 운전자 + 적용할 프로필 (없으면 204) | 200/204 |
| POST | `/me/next-driver/{reservationId}/applied` | 적용 완료 보고 (재시도 안전, 취소된 예약은 409) | 200 |

### 공통 규칙

- 남의 프로필이나 내가 멤버가 아닌 차량은 404로 응답합니다(존재 여부 비노출).
- 오류는 모두 RFC 9457 ProblemDetail(`application/problem+json`)이고, `detail`에 한국어 설명이 들어 있습니다.

## 프로필 설정값

- 프로필 수에는 제한이 없습니다. 순정 프로필(최대 2명)과의 차별점입니다.
- POST·PUT 모두 `name`만 필수입니다. 빠진 설정 묶음은 기본값으로 채워집니다.
- PUT은 전체 교체입니다. 보내지 않은 묶음은 기본값으로 돌아가므로, 폰 앱은 프로필 전체를 보내야 합니다.

### 요청 예시

```json
{
  "name": "대학생 자녀",
  "display": { "themeColor": "#FF5722", "wallpaperId": "night-city", "widgets": ["music", "weather", "battery"] },
  "seat": { "reclineAngle": 110, "slidePosition": 70, "heatingLevel": 2, "steeringWheelHeating": true },
  "climate": { "temperature": 20.5, "fanSpeed": 5 },
  "light": { "color": "#7C4DFF", "brightness": 80 },
  "welcomeSoundId": "chime-01"
}
```

### 필드와 허용 범위

범위는 임시 기준입니다. Vehicle SDK 검증 후 차종 프로파일에 맞춰 조정합니다.

| 필드 | 기본값 | 허용 범위 | 대응 SDK |
| --- | --- | --- | --- |
| `name` | 필수 | 공백 불가, 20자 이하 | — |
| `display.themeColor` | `#1E88E5` | `#RRGGBB` | 앱 내 커스텀 홈 |
| `display.wallpaperId` | 없음 | 100자 이하 | 앱 내 커스텀 홈 |
| `display.widgets` | `[]` | 최대 12개, 순서 유지 | 앱 내 커스텀 홈 |
| `seat.reclineAngle` | 100 | 80–150 | Seat |
| `seat.slidePosition` | 50 | 0–100 | Seat |
| `seat.heatingLevel` | 0 | 0–3 | Seat |
| `seat.steeringWheelHeating` | false | true/false | Steeringwheel |
| `climate.temperature` | 22.0 | 16.0–32.0 | HVAC |
| `climate.fanSpeed` | 3 | 0–8 | HVAC |
| `light.color` | `#FFFFFF` | `#RRGGBB` | Light (제어 가능 여부 미확정) |
| `light.brightness` | 50 | 0–100 | Light |
| `welcomeSoundId` | 없음 | 100자 이하 | 시동 감지 후 앱이 재생 |

## 구조

```
src/main/kotlin/com/pleos/backend
├── common/        # BaseEntity, 오류 응답(ProblemDetail), 토큰 유틸, 웹·Swagger 설정
├── auth/          # Bearer 토큰 인터셉터, AuthUser·AuthVehicle 주입
├── user/          # 가입·내 정보
├── profile/       # 프로필 엔티티·DTO·서비스·컨트롤러
├── vehicle/       # 차량, 멤버(OWNER/MEMBER), 페어링
├── reservation/   # 다음 운전자 예약 (폰 앱 쪽 API)
└── vehicleapp/    # 차량 앱 전용 API
```

## 다음 작업 후보

단계별 계획은 [docs/DESIGN.md](docs/DESIGN.md#6-단계별-로드맵)에 있습니다.

- 테마 공유 링크 (프로필 → 공유 코드 → 복사)
- 멤버 관리(내보내기·나가기), 페어링 코드 재발급
- 운영 DB(PostgreSQL + Flyway) 전환과 배포

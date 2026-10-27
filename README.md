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

## QA 콘솔 (임시 화면)

화면이 나오기 전까지 기능 QA에 쓰는 웹 화면입니다. 서버를 띄우고 `http://localhost:8090/qa/`를 엽니다.

- **폰 앱 쪽**: 가입·닉네임, 페어링, 차량 멤버 관리(OWNER 넘기기·내보내기·코드 재발급·나가기), 테마(색·이미지 업로드·버튼/위젯 모양)·공유·마켓, 프로필·시트 슬롯·다음 운전자 예약
- **차량 앱 쪽**: 차량 등록·페어링 코드, 탑승 2단계 적용 시뮬레이션(SDK 실패 흉내 포함), 1탭 선택, 지금 자세 저장, 테마가 입혀진 차량 화면 미리보기
- **데모 가족 만들기**: 엄마(OWNER)·아빠·대학생 자녀, 테마 2개, 프로필 3개, 시트 슬롯, 자녀의 다음 운전자 예약을 한 번에 생성(`POST /qa/api/seed`)
- **자동 QA 실행**: 데모 시나리오와 예외 상황 20단계를 실제 API로 순서대로 검사하고 통과/실패를 표시
- **요청 로그**: 모든 요청·응답을 펼쳐 볼 수 있음

운영 배포에서는 `pleos.qa.enabled=false`로 끕니다(콘솔과 시드 API가 모두 사라짐). QA 기록은 [docs/QA.md](docs/QA.md)에 있습니다.

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
| PATCH | `/users/me` | 닉네임 변경 (웰컴 인사말에 바로 반영) | 200 |
| GET | `/profiles` | 내 프로필 목록 (생성 순) | 200 |
| GET | `/profiles/{id}` | 프로필 조회 | 200 |
| POST | `/profiles` | 프로필 생성 | 201 + `Location` |
| PUT | `/profiles/{id}` | 프로필 전체 교체 | 200 |
| DELETE | `/profiles/{id}` | 프로필 삭제 | 204 |
| GET | `/profiles/{id}/seat-positions` | 차량별 시트 자세 목록 | 200 |
| PUT | `/profiles/{id}/seat-positions/{vehicleId}` | 차량별 시트 자세 저장 (보통 메모리 슬롯만) | 200 |
| DELETE | `/profiles/{id}/seat-positions/{vehicleId}` | 차량별 시트 자세 지우기 (그 차에서는 시트를 움직이지 않음) | 204 |
| POST | `/vehicles/pair` | 차량 화면의 6자리 코드로 페어링 (첫 사람 OWNER) | 200 |
| GET | `/vehicles` | 내 차량 목록 | 200 |
| GET | `/vehicles/{vehicleId}` | 차량 상세 + 멤버 | 200 |
| PATCH | `/vehicles/{vehicleId}` | 차 이름 변경 (OWNER만) | 200 |
| POST | `/vehicles/{vehicleId}/pairing-code` | 페어링 코드 재발급, 이전 코드 즉시 무효 (OWNER만) | 200 |
| POST | `/vehicles/{vehicleId}/owner` | OWNER 넘기기 `{userId}` (OWNER만) | 200 |
| DELETE | `/vehicles/{vehicleId}/members/me` | 차량에서 나가기 (다른 멤버가 있으면 OWNER는 먼저 넘겨야 함, 409) | 204 |
| DELETE | `/vehicles/{vehicleId}/members/{userId}` | 멤버 내보내기 (OWNER만) | 204 |
| PUT | `/vehicles/{vehicleId}/next-driver` | 다음 운전자 예약 `{profileId}` | 200 |
| GET | `/vehicles/{vehicleId}/next-driver` | 현재 대기 예약 (없으면 204) | 200/204 |
| DELETE | `/vehicles/{vehicleId}/next-driver` | 대기 예약 취소 (예약한 사람이나 OWNER만, 다른 MEMBER는 403) | 204 |
| POST | `/assets` | 테마 이미지 업로드 (multipart `file`, PNG·JPEG·WEBP, 3MB 이하, GIF 불가) | 201 |
| GET | `/themes` | 내 테마 목록 (직접 만든 것 + 공유받은 것) | 200 |
| GET | `/themes/{id}` | 테마 조회 | 200 |
| POST | `/themes` | 테마 생성 (글자 대비 부족하면 400) | 201 |
| PUT | `/themes/{id}` | 테마 전체 교체 | 200 |
| DELETE | `/themes/{id}` | 테마 삭제 (쓰던 프로필은 기본 테마로) | 204 |
| POST | `/themes/{id}/share` | 공유 시작 → 8자리 공유 코드 (공유받은 테마는 409) | 200 |
| DELETE | `/themes/{id}/share` | 공유 중지 | 204 |
| GET | `/themes/shared/{shareCode}` | 공유 코드 미리보기 | 200 |
| POST | `/themes/import` | 공유 코드로 받기 `{shareCode}` → 내 테마로 복사 | 201 |
| GET | `/themes/market` | 테마 마켓 (공유 중, 많이 받은 순 50개) | 200 |

이미지는 `GET /files/{id}`로 토큰 없이 내려받습니다(경로는 `/api/v1` 밖). 추측할 수 없는 UUID 주소이고 내용이 바뀌지 않아 1년 캐시합니다.

### 차량 앱

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/vehicles` | 차량 등록 (첫 실행 1회, 토큰 불필요) | 201 |
| GET | `/me` | 내 차량 정보 (페어링 코드·멤버 수) | 200 |
| GET | `/me/profiles` | 멤버별 프로필, 차량 설정만 (1탭 선택 화면용) | 200 |
| GET | `/me/next-driver` | 1단계: 다음 운전자 + 차량 설정 (없으면 204) | 200/204 |
| POST | `/me/next-driver/{reservationId}/applied` | 적용 완료 보고 → 2단계 웰컴 묶음(테마·조명·사운드·인사말). 재시도 안전, 취소된 예약은 409 | 200 |
| POST | `/me/profiles/{profileId}/applied` | 1탭으로 고른 프로필 적용 완료 보고 → 2단계 웰컴 묶음 | 200 |
| PUT | `/me/profiles/{profileId}/seat-position` | 지금 자세 저장: SDK로 읽은 현재 시트 위치·메모리 슬롯을 이 차량 기준으로 저장 | 200 |

차량 앱은 **차량 설정(시트·공조·열선)을 먼저 적용하고 보고한 뒤**, 응답으로 받은 테마·앰비언트 라이트·웰컴 사운드를 적용합니다. 테마가 먼저 바뀌지 않도록 응답을 두 단계로 나눴습니다.

### 공통 규칙

- 남의 프로필이나 내가 멤버가 아닌 차량은 404로 응답합니다(존재 여부 비노출). 같은 차 안에서 권한이 모자라면(MEMBER가 OWNER 기능 호출 등) 403입니다.
- 멤버가 나가거나 내보내지면 그 사람의 대기 예약은 취소되고, 그 차량에 저장한 시트 자세는 지워집니다. 프로필 자체는 남습니다.
- 동시에 같은 요청이 겹쳐 데이터 제약에 걸리면 409, 예상하지 못한 오류는 내부 정보 없이 500 ProblemDetail로 응답합니다.
- 오류는 모두 RFC 9457 ProblemDetail(`application/problem+json`)이고, `detail`에 한국어 설명이 들어 있습니다.

## 프로필 설정값

- 프로필 수에는 제한이 없습니다. 순정 프로필(최대 2명)과의 차별점입니다.
- POST·PUT 모두 `name`만 필수입니다. 빠진 설정 묶음은 기본값으로 채워집니다.
- PUT은 전체 교체입니다. 보내지 않은 묶음은 기본값으로 돌아가므로, 폰 앱은 프로필 전체를 보내야 합니다.

### 요청 예시

```json
{
  "name": "대학생 자녀",
  "themeId": "3f0c…",
  "display": { "widgets": ["music", "weather", "battery"] },
  "seat": { "heatingLevel": 2, "ventilationLevel": 1, "steeringWheelHeating": true },
  "climate": { "temperature": 20.5, "fanSpeed": 5 },
  "light": { "brightness": 80 },
  "welcomeSoundId": "chime-01"
}
```

색·배경·캐릭터·버튼 모양·앰비언트 색은 프로필이 아니라 **테마**에 있습니다. 프로필은 `themeId`로 테마를 가리킵니다.

### 시트 자세는 차량별로 따로

Pleos Vehicle SDK는 시트를 절대 각도로 지정할 수 없고(`moveSeat*`는 FORWARD/STOP/BACKWARD), 위치값의 범위·단위도 차종마다 다릅니다. 그래서 시트 자세는 프로필이 아니라 **프로필 × 차량**별로 저장합니다.

| 필드 | 의미 | 누가 채우나 |
| --- | --- | --- |
| `memorySlot` | 차량 시트 메모리 번호(`selectSeatMemory`), 1–10 임시 | 폰 앱에서 선택 |
| `backrestPosition` · `foreAftPosition` | 차량 단위 그대로의 현재 위치 | 차량 앱 "지금 자세 저장" |

차량 앱은 메모리 슬롯이 있으면 그것을 먼저 쓰고, 없으면 위치를 구독하며 목표에서 STOP을 보냅니다. 저장된 자세가 없으면 `vehicleSettings.seatPosition`이 null이고 시트를 움직이지 않습니다.

### 필드와 허용 범위

범위는 임시 기준입니다. Vehicle SDK 검증 후 차종 프로파일에 맞춰 조정합니다.

| 필드 | 기본값 | 허용 범위 | 대응 SDK |
| --- | --- | --- | --- |
| `name` | 필수 | 공백 불가, 20자 이하 | — |
| `themeId` | 없음(기본 테마) | 내 테마 ID | 차량 앱 화면 |
| `display.widgets` | `[]` | 최대 12개, 순서 유지 | 차량 앱 홈 |
| `seat.heatingLevel` | 0 | 0–3 | HVAC `setHvacSeatTemperature` |
| `seat.ventilationLevel` | 0 | 0–3 | HVAC `setHvacSeatVentilation` |
| `seat.steeringWheelHeating` | false | true/false | HVAC `setHvacSteeringWheelHeat` |
| `climate.temperature` | 22.0 | 16.0–32.0 | HVAC |
| `climate.fanSpeed` | 3 | 0–8 | HVAC |
| `light.brightness` | 50 | 0–100 | Light (색은 테마의 `ambientColor`) |
| `welcomeSoundId` | 없음 | 100자 이하 | 차량 설정 적용 후 앱이 재생 |

## 테마 설정값

카카오톡 테마처럼 **차량 앱 화면 안**의 겉모습을 바꿉니다. 차량 런처·계기판은 OS 영역이라 바꿀 수 없습니다.

| 필드 | 기본값 | 값 |
| --- | --- | --- |
| `name` | 필수 | 20자 이하 |
| `colors.background` / `surface` / `primary` / `onPrimary` / `text` | `#121212` / `#1E1E1E` / `#1E88E5` / `#FFFFFF` / `#FFFFFF` | `#RRGGBB` |
| `backgroundAssetId` | 없음 | 업로드한 이미지 ID |
| `characterAssetId` | 없음 | 업로드한 이미지 ID |
| `characterPlacement` | `BOTTOM_RIGHT` | `BOTTOM_RIGHT` `BOTTOM_LEFT` `TOP_RIGHT` `WELCOME_ONLY` |
| `buttonStyle` | `ROUNDED` | `ROUNDED` `PILL` `SQUARE` `OUTLINED` |
| `widgetStyle` | `FILLED` | `FILLED` `OUTLINED` `GLASS`(배경 위 반투명) |
| `fontStyle` | `DEFAULT` | `DEFAULT` `ROUNDED` `BOLD` |
| `ambientColor` | `#FFFFFF` | `#RRGGBB`, 실내 앰비언트 라이트 색 |

- **운전 중 가독성 검사**: 글자/배경·카드 4.5:1, 버튼 글자/버튼 3:1 이상이 아니면 400입니다(WCAG 명도 대비).
- 스타일 값(`buttonStyle` 등)은 차량 앱이 화면을 그리는 규격입니다. 값을 늘리면 차량 앱도 같이 대응해야 합니다.
- 공유받은 테마는 내 소유로 복사됩니다. 원작자의 이미지는 그대로 쓸 수 있지만, 다시 공유할 수는 없습니다.

## 구조

```
src/main/kotlin/com/pleos/backend
├── common/        # BaseEntity, 오류 응답(ProblemDetail), 토큰 유틸, 웹·Swagger 설정
├── auth/          # Bearer 토큰 인터셉터, AuthUser·AuthVehicle 주입
├── user/          # 가입·내 정보
├── profile/       # 프로필 엔티티·DTO·서비스·컨트롤러
├── vehicle/       # 차량, 멤버(OWNER/MEMBER), 페어링
├── reservation/   # 다음 운전자 예약 (폰 앱 쪽 API)
├── vehicleapp/    # 차량 앱 전용 API
├── theme/         # 테마(색·배경·캐릭터·버튼/위젯 모양), 공유·마켓, 웰컴 묶음 조립
├── asset/         # 테마 이미지 업로드·내려받기
└── qa/            # 임시 QA 콘솔(/qa)과 데모 시드 API (개발 서버 전용)
```

## 다음 작업 후보

단계별 계획은 [docs/DESIGN.md](docs/DESIGN.md#6-단계별-로드맵)에 있습니다.

- 운영 DB(PostgreSQL + Flyway) 전환과 배포

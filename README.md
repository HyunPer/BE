# Pleos Backend

현대차 Pleos 차량용 앱 공모전 백엔드 서버입니다.
폰 앱에서 만든 프로필(디스플레이·시트·공조·라이트·웰컴 사운드)을 저장하고, 차량 앱이 받아서 적용합니다.

기준 문서: [Notion 공모전 페이지](https://app.notion.com/p/Pleos-4044c75bb8a781d4aa5ecb3bfcca8074)

## 스택

| 항목 | 버전 |
| --- | --- |
| Kotlin | 2.3.21 |
| Spring Boot | 4.1.2 (Web MVC, Data JPA, Validation, Actuator) |
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
- DB 콘솔: `http://localhost:8090/h2-console` (JDBC URL `jdbc:h2:file:./data/pleos`, 사용자 `sa`, 비밀번호 없음)
- Android 에뮬레이터에서는 호스트 PC를 `http://10.0.2.2:8090`으로 접근합니다.

## 테스트

```bash
./gradlew test
```

## 프로필 API

Base path: `/api/v1/profiles`

| 메서드 | 경로 | 설명 | 성공 응답 |
| --- | --- | --- | --- |
| GET | `/` | 프로필 목록 (생성 순) | 200 |
| GET | `/{id}` | 프로필 조회 (차량 앱이 시동 시 호출) | 200 |
| POST | `/` | 프로필 생성 | 201 + `Location` |
| PUT | `/{id}` | 프로필 전체 교체 | 200 |
| DELETE | `/{id}` | 프로필 삭제 | 204 |

- 프로필 수에는 제한이 없습니다. 순정 프로필(최대 2명)과의 차별점입니다.
- POST·PUT 모두 `name`만 필수입니다. 빠진 설정 묶음은 기본값으로 채워집니다.
- PUT은 전체 교체입니다. 보내지 않은 묶음은 기본값으로 돌아가므로, 폰 앱은 프로필 전체를 보내야 합니다.
- 오류는 모두 RFC 9457 ProblemDetail(`application/problem+json`)로 응답합니다. 검증 실패는 400, 없는 ID는 404입니다.

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
├── PleosBackendApplication.kt
├── ApiExceptionHandler.kt        # 전역 ProblemDetail 오류 응답
└── profile/
    ├── Profile.kt                # 엔티티 + 설정 묶음(Embeddable)
    ├── ProfileDtos.kt            # 요청/응답 DTO, 검증 규칙, 변환
    ├── ProfileService.kt         # Repository, 서비스, 404 예외
    └── ProfileController.kt
```

## 다음 작업 후보

- 사용자 계정·인증, 프로필 소유자 연결
- 차량 등록과 차량-사용자 매핑
- 다음 운전자 예약 API (차량 앱이 시동 시 조회)
- 운영 DB(PostgreSQL) 전환

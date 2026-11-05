# 쉼표 Backend

현대차그룹 차량용 앱 개발 공모전(Pleos) 출품작 **쉼표**의 서버입니다. 팀 귤까먹는 헬창.

> **쉼표**: 함께 타는 사람의 몸(멀미)과 마음(정서)을 챙기는 차량 앱.
> 차량 앱이 차량 상태 조회(Vehicle SDK)와 내비 정보(NaviHelper)로 길을 살펴 멀미 위험을 미리 알려 주고, 도착하면 폰에서 오늘 이동을 한 줄로 남깁니다.

이 서버는 가족·차량 연결, 트립 기록, 도착 체크인, 탑승자 컨디션을 저장합니다. 클라이언트는 [FE 저장소](https://github.com/HyunPer/FE)에 있습니다.
예전 "가족 공유차 프로필" 버전(테마·예약·차량 제어)은 `backup/family-profile` 브랜치에 남아 있습니다.

설계: [docs/DESIGN.md](docs/DESIGN.md)

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

- 기본 포트는 `8090`입니다. 다른 포트는 `PORT` 환경변수로 지정합니다. 8090을 다른 프로그램이 쓰고 있으면 서버가 뜨지 않습니다.
- 헬스 체크: `http://localhost:8090/actuator/health`
- **API 문서(Swagger UI): `http://localhost:8090/swagger-ui.html`**. 폰 앱·차량 앱 그룹으로 나뉘어 있고, Authorize에 토큰을 넣으면 바로 호출할 수 있습니다.
- DB 콘솔: `http://localhost:8090/h2-console` (JDBC URL `jdbc:h2:file:./data/pleos`, 사용자 `sa`, 비밀번호 없음)
- Android 에뮬레이터에서는 호스트 PC를 `http://10.0.2.2:8090`으로 접근합니다.

## 테스트

```bash
./gradlew test
```

가입·페어링·멤버 관리, 트립 시작·종료·체크인·컨디션, 오류 응답 형식을 MockMvc로 확인합니다(15개).
FE 저장소의 E2E 테스트로 실제 서버를 상대로 쉼표 흐름 전체를 확인할 수 있습니다.

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
| PATCH | `/users/me` | 닉네임 변경 | 200 |
| GET | `/users/me/condition` | 내 컨디션 (멀미 민감도·쉬는 방식, 저장 전이면 기본값) | 200 |
| PUT | `/users/me/condition` | 내 컨디션 저장 | 200 |
| POST | `/vehicles/pair` | 차량 화면의 6자리 코드로 페어링 (첫 사람 OWNER) | 200 |
| GET | `/vehicles` | 내 차량 목록 | 200 |
| GET | `/vehicles/{vehicleId}` | 차량 상세 + 멤버 + 가족 초대용 연결 코드 | 200 |
| PATCH | `/vehicles/{vehicleId}` | 차 이름 변경 (OWNER만) | 200 |
| POST | `/vehicles/{vehicleId}/pairing-code` | 페어링 코드 재발급, 이전 코드 즉시 무효 (OWNER만) | 200 |
| POST | `/vehicles/{vehicleId}/owner` | OWNER 넘기기 `{userId}` (OWNER만) | 200 |
| DELETE | `/vehicles/{vehicleId}/members/me` | 차량에서 나가기 (다른 멤버가 있으면 OWNER는 먼저 넘겨야 함, 409) | 204 |
| DELETE | `/vehicles/{vehicleId}/members/{userId}` | 멤버 내보내기 (OWNER만) | 204 |
| GET | `/vehicles/{vehicleId}/trips?limit=20` | 최근 트립(최신순) + 내 체크인. limit 1–50 | 200 |
| PUT | `/trips/{tripId}/check-in` | 도착 체크인 `{mood, note}`. 다시 남기면 덮어씀 | 200 |

### 차량 앱

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/vehicles` | 차량 등록 (첫 실행 1회, 토큰 불필요) | 201 |
| GET | `/me` | 내 차량 정보 (페어링 코드·멤버 수) | 200 |
| GET | `/me/riders` | 가족별 컨디션 (멀미 위험도 배율에 사용) | 200 |
| POST | `/trips` | 트립 시작 (탑승 감지 때). 끝내지 못한 이전 트립은 닫힘 | 201 |
| POST | `/trips/{tripId}/finish` | 트립 종료 + 주행 요약. 재시도해도 같은 응답 | 200 |

### 값

| 항목 | 값 |
| --- | --- |
| `mood` | `CALM`(편안) · `OK`(괜찮음) · `TIRED`(피곤) · `STRESSED`(답답) · `QUEASY`(속이 안 좋음) |
| `note` | 200자 이하, 선택 |
| `motionSensitivity` | `LOW`(거의 안 탐) · `MEDIUM`(가끔) · `HIGH`(잘 탐) |
| `restStyle` | `QUIET` · `BREATHING` · `MUSIC` · `FRESH_AIR` |
| 주행 요약 | `drivingMinutes`·`congestionMinutes` 0–1440, `sharpTurns` 0–1000, `peakRisk` 0–100 |

### 공통 규칙

- 내가 멤버가 아닌 차량과 그 차의 트립은 404로 응답합니다(존재 여부 비노출). 같은 차 안에서 권한이 모자라면(MEMBER가 OWNER 기능 호출 등) 403입니다.
- 체크인은 상담이 아니라 본인이 남기는 기록입니다. 서버는 해석하지 않고 저장만 합니다.
- 동시에 같은 요청이 겹쳐 데이터 제약에 걸리면 409, 예상하지 못한 오류는 내부 정보 없이 500 ProblemDetail로 응답합니다.
- 오류는 모두 RFC 9457 ProblemDetail(`application/problem+json`)이고, `detail`에 한국어 설명이 들어 있습니다.

## 구조

```
src/main/kotlin/com/pleos/backend
├── common/        # BaseEntity, 오류 응답(ProblemDetail), 토큰 유틸, 웹·Swagger 설정
├── auth/          # Bearer 토큰 인터셉터, AuthUser·AuthVehicle 주입
├── user/          # 가입·내 정보
├── vehicle/       # 차량, 멤버(OWNER/MEMBER), 페어링
├── vehicleapp/    # 차량 앱 등록·내 차량 정보
└── trip/          # 쉼표: 트립, 도착 체크인, 탑승자 컨디션
```

## 다음 작업 후보

- 운영 DB(PostgreSQL + Flyway) 전환과 HTTPS 배포(제출 APK는 HTTPS만 접속)
- 주간 리포트(기분·막힘 패턴) API

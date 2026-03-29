# 🏢 FollowMe Gateway Server (API Gateway)

## 1. 프로젝트 개요 (Executive Summary)
`gateway-server`는 FollowMe 마이크로서비스 아키텍처(MSA)의 Single Entry Point 역할을 수행하는 핵심 인프라입니다. 클라이언트의 모든 요청은 본 게이트웨이를 거쳐 내부 서비스로 라우팅되며, 이 과정에서 중앙 집중식 보안 인증과 시스템 성능 감사를 수행합니다.

## 2. 핵심 비즈니스 가치 (Core Capabilities)

* **보안 통제소 (Zero-Trust Security):** `CustomAuthFilter`를 통해 Keycloak에서 발급한 JWT(JSON Web Token)의 유효성을 최전선에서 검증하여, 인가되지 않은 외부 접근을 원천 차단합니다.
* **동적 라우팅 (Service Discovery):** Netflix Eureka와 연동하여 백엔드 서비스(예: `USER-SERVER`)의 위치(IP/Port)를 동적으로 파악하고 트래픽을 안전하게 분산(Load Balancing)합니다.
* **운영 가시성 확보 (Global Audit Logging):** `GlobalLoggingFilter`를 통해 모든 API 요청의 진입점과 처리 소요 시간을 밀리초(ms) 단위로 추적하여 시스템 병목 현상을 모니터링합니다.

---

## 3. 시스템 아키텍처 (Architecture Flow)

요청(Request)은 다음과 같은 보안 및 라우팅 파이프라인을 거칩니다.

1. **Client** ➡️ `http://localhost:8000/api/v1/...` (Gateway API 호출)
2. **Global Pre Filter** ➡️ 요청 시간 기록 및 로깅
3. **Custom Auth Filter** ➡️ `Authorization: Bearer <Token>` 검증 (실패 시 401 차단)
4. **Eureka Registry** ➡️ 목적지 마이크로서비스 주소 탐색 (예: `lb://USER-SERVER`)
5. **Microservice** ➡️ 실제 비즈니스 로직 처리 (`user-server`)
6. **Global Post Filter** ➡️ 최종 응답 속도 산출 및 로깅

---

## 4. 인프라 기동 순서 (Standard Operating Procedure)

마이크로서비스 간의 의존성으로 인해 **반드시 아래의 순서대로 서버를 기동**해야 합니다.

1. **Eureka Server** (`8761`): 주소록 역할 (가장 먼저 기동)
2. **Keycloak Server** (`9090`): 인증 및 토큰 발급 (SSO)
3. **Microservices** (예: `user-server` `8080`): 실제 업무 처리 서비스
4. **Gateway Server** (`8000`): 최종 라우팅 및 보안 관제

> **인프라 검증:** 브라우저에서 `http://localhost:8761` (Eureka Dashboard)에 접속하여 `GATEWAY-SERVER`와 타겟 서비스들이 **UP** 상태인지 반드시 확인하십시오.

---

## 5. API 테스트 지침 (QA Testing Protocol)

게이트웨이를 통한 통합 테스트 시, Postman 또는 cURL을 사용하여 다음 규격을 준수해야 합니다.

### ❌ 잘못된 테스트 방식 (Direct Bypass)
* **URL:** `http://localhost:8080/api/v1/users/...`
* **사유:** 게이트웨이(8000)를 우회하여 내부망(8080)으로 직접 찔러넣는 방식이므로 보안 필터와 로그가 작동하지 않습니다.

### ⭕ 올바른 테스트 방식 (Via Gateway)
* **URL:** `http://localhost:8000/api/v1/users/...`
* **Headers 필수 포함:**
  * `Key`: `Authorization`
  * `Value`: `Bearer {Keycloak에서_새로_발급받은_Access_Token}`

```bash
# cURL 테스트 예시
curl -X GET "http://localhost:8000/api/v1/users/profile" \
     -H "Authorization: Bearer eyJhbGciOi..."

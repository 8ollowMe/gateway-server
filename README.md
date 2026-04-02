# 🚀 API Gateway Server

B2B 물류 배송 시스템(SpartaHub)의 단일 진입점(Single Point of Contact)을 담당하는 API Gateway입니다. 외부 트래픽을 각 마이크로서비스로 라우팅하고, 중앙 집중식 인증 및 인가를 수행합니다.

## ✨ 핵심 기능

- **동적 라우팅:** Eureka Service Discovery를 통해 대상 서버로 트래픽을 로드밸런싱합니다.
- **실시간 권한 검증 (Stateful Auth):** 외부 요청의 위조된 헤더를 초기화하고, JWT 토큰을 바탕으로 User Server와 내부 통신(`WebClient`)하여 유저의 실시간 상태(`APPROVED`)를 검증합니다.
- **글로벌 CORS 처리:** 프론트엔드 연동을 위한 전역 CORS 설정을 관리하여 하위 서비스의 부담을 줄입니다.

---

## 🗺️ 라우팅 규격 (Architecture)

외부에서 들어오는 모든 요청은 아래의 경로 규칙에 따라 각 마이크로서비스로 전달됩니다.

| 마이크로서비스 | 라우팅 경로 (Predicates) | 비고 |
|---|---|---|
| **User Server** | `/api/v1/users/**` | 회원가입(`/register`)은 인증 필터 패스 |
| **Hub Server** | `/api/v1/hubs/**`, `/api/v1/hub-routes/**`, `/api/v1/stocks/**`, `/api/v1/stock-histories/**` | |
| **Vendor Server** | `/api/v1/vendors/**`, `/api/v1/products/**` | |
| **Order Server** | `/api/v1/orders/**` | |
| **Delivery Server** | `/api/v1/deliveries/**`, `/api/v1/shipments/**` | |
| **Message Server**| `/api/v1/slack-messages/**`, `/api/v1/notifications/**` | |
| **AI Server** | `/api/v1/ai/**` | |

> ⚠️ **주의:** `/internal/**` 로 시작하는 내부 통신용 API는 라우팅 규칙에서 제외되어 있으므로 외부(Postman, Frontend)에서 직접 호출할 수 없습니다.

---

## 🔑 내부 인증 헤더 (X-Headers) 사용 가이드

**API Gateway를 통과한 모든 요청에는 유저의 최신 정보가 HTTP Header에 담겨서 전달됩니다.**
따라서 뒷단(Downstream)의 마이크로서비스 개발자들은 복잡한 JWT 검증 로직을 구현할 필요 없이, Request Header에서 값을 꺼내 쓰기만 하면 됩니다.

### 📦 전달되는 헤더 목록

| 헤더 이름 (Key) | 설명 (Value) | 예시 |
|---|---|---|
| `X-User-Id` | 유저의 고유 식별자 (UUID) | `123e4567-e89b-12d3-a456-426614174000` |
| `X-Role` | 유저의 권한 등급 | `MASTER`, `HUB_MANAGER`, `VENDOR` 등 |
| `X-Username` | 로그인 아이디 | `test_user_01` |
| `X-User-Name` | 유저의 실제 이름 (URL 인코딩됨) | `%ED%99%8D%EA%B8%B8%EB%8F%99` (홍길동) |
| `X-User-Email` | 유저 이메일 | `user@example.com` |

### 💻 각 마이크로서비스에서 X-Header를 사용하는 방법 (Spring Boot 예시)

해당 마이크로서비스의 Controller에서 `@RequestHeader` 어노테이션을 사용하여 필요한 정보를 바로 꺼내어 비즈니스 로직에 활용하세요.

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
public class OrderController {

    @PostMapping("/api/v1/orders")
    public ResponseEntity<String> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Role") String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName
    ) {
        // 1. 권한 체크 예시 (Service 계층으로 넘겨서 처리해도 무방)
        if (!role.equals("MASTER") && !role.equals("HUB_MANAGER")) {
            return ResponseEntity.status(403).body("주문 생성 권한이 없습니다.");
        }

        // 2. 인코딩된 이름 디코딩 예시 (필요한 경우)
        String realName = "";
        if (encodedName != null) {
            realName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        }

        // 3. 추출한 userId를 바탕으로 주문 로직 실행
        // orderService.createOrder(userId, requestDto);

        return ResponseEntity.ok(realName + "님의 주문이 접수되었습니다. (ID: " + userId + ")");
    }
}
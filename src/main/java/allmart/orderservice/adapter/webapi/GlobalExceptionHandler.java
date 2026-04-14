package allmart.orderservice.adapter.webapi;

import allmart.orderservice.domain.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 전역 예외 처리기 — 도메인/유효성/서비스 간 오류를 표준 에러 응답 형식으로 변환.
 * 코드 매핑: ORDER_NOT_FOUND(404), BAD_REQUEST(400), INVALID_STATE(409), VALIDATION_ERROR(400), INTERNAL_ERROR(500)
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleOrderNotFound(OrderNotFoundException e) {
        log.warn("주문 없음: {}", e.getMessage());
        return Map.of("code", "ORDER_NOT_FOUND", "message", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return Map.of("code", "BAD_REQUEST", "message", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalState(IllegalStateException e) {
        log.warn("상태 전이 오류: {}", e.getMessage());
        return Map.of("code", "INVALID_STATE", "message", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e) {
        // 첫 번째 필드 오류 메시지만 반환
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("입력값 오류");
        log.warn("유효성 검증 실패: {}", message);
        return Map.of("code", "VALIDATION_ERROR", "message", message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        log.warn("요청 거부 ({}): {}", e.getStatusCode(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("code", "FORBIDDEN", "message",
                        e.getReason() != null ? e.getReason() : "접근이 거부되었습니다."));
    }

    /** product-service / inventory-service 등 내부 서비스 4xx → 그대로 클라이언트에 전달 */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleHttpClientError(HttpClientErrorException e) {
        log.warn("내부 서비스 오류 ({}): {}", e.getStatusCode(), e.getMessage());
        String message = extractMessage(e.getResponseBodyAsString());
        return org.springframework.http.ResponseEntity
                .status(e.getStatusCode())
                .body(Map.of("code", "BAD_REQUEST", "message", message));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpected(Exception e) {
        log.error("예상치 못한 오류: {}", e.getMessage(), e);
        return Map.of("code", "INTERNAL_ERROR", "message", "서버 오류가 발생했습니다.");
    }

    /** 내부 서비스 에러 응답 본문에서 message 필드 추출 — 파싱 실패 시 원문 반환 */
    private String extractMessage(String body) {
        try {
            return objectMapper.readTree(body).path("message").asText(body);
        } catch (Exception e) {
            return body;
        }
    }
}

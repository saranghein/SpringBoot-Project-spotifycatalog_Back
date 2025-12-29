package com.musicinsights.spotifycatalog.application.common.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * 전역 예외 처리기.
 *
 * <p>애플리케이션 전반의 예외를 {@link ErrorResponse} 형태로 변환하여 반환한다.</p>
 */
@Order(-2)
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * {@link BadRequestException}을 400 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 400 ErrorResponse
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        400,
                        "Bad Request",
                        e.getMessage(),
                        path,
                        e.code()
                ));
    }


    /**
     * {@link NotFoundException}을 404 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 404 ErrorResponse
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "Not Found",
                        e.getMessage(),
                        path,
                        e.code()
                ));
    }

    /**
     * DB 접근/SQL 오류를 500 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 500 ErrorResponse(DB_ERROR)
     */
    @ExceptionHandler({DataAccessException.class, BadSqlGrammarException.class})
    public ResponseEntity<ErrorResponse> handleDb(Exception e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal Server Error",
                        "Database error",
                        path,
                        "DB_ERROR"
                ));
    }

    /**
     * 처리되지 않은 예외를 500 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 500 ErrorResponse(INTERNAL_ERROR)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal Server Error",
                        "Unexpected error",
                        path,
                        "INTERNAL_ERROR"
                ));
    }

    /**
     * 검증(ConstraintViolation) 실패를 400 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 400 ErrorResponse(VALIDATION_ERROR)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();

        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", msg, path, "VALIDATION_ERROR"));
    }

    /**
     * 바인딩/파라미터 검증(WebExchangeBind) 실패를 400 응답으로 변환한다.
     *
     * @param e  예외
     * @param ex 요청 컨텍스트
     * @return 400 ErrorResponse(VALIDATION_ERROR)
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleWebExchangeBind(WebExchangeBindException e, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().value();

        String msg = e.getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", msg, path, "VALIDATION_ERROR"));
    }
}

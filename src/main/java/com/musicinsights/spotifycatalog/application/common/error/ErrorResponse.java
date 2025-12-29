package com.musicinsights.spotifycatalog.application.common.error;

import java.time.Instant;

/**
 * 공통 에러 응답 DTO.
 *
 * @param timestamp 에러 발생 시각
 * @param status    HTTP 상태 코드
 * @param error     HTTP 상태 메시지
 * @param message   에러 메시지
 * @param path      요청 경로
 * @param code      애플리케이션 에러 코드
 */
public record ErrorResponse(
   Instant timestamp,
   int status,
   String error,
   String message,
   String path,
   String code
) {
    public static ErrorResponse of(int status,String error,String message, String path, String code){
        return new ErrorResponse(Instant.now(),status,error,message,path,code);
    }
}

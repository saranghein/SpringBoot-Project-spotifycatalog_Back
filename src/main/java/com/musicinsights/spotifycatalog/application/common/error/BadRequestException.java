package com.musicinsights.spotifycatalog.application.common.error;

/**
 * 400 Bad Request 상황을 표현하는 커스텀 예외.
 *
 * <p>에러 응답 구성을 위한 code 값을 함께 보관한다.</p>
 */
public class BadRequestException extends RuntimeException{
    private final String code;

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    /**
     * 에러 코드를 반환한다.
     *
     * @return 에러 코드
     */
    public String code(){
        return code;
    }
}

package com.musicinsights.spotifycatalog.application.common.error;

public class NotFoundException extends RuntimeException{
    private final String code;

    public NotFoundException(String message, String code) {
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

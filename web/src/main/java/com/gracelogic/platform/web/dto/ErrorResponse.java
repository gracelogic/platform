package com.gracelogic.platform.web.dto;

/**
 * Author: Igor Parkhomenko
 * Date: 24.12.2014
 * Time: 15:55
 */
public class ErrorResponse extends PlatformResponse {
    private String code = "";
    private String message = "";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorResponse() {
    }

    public ErrorResponse(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
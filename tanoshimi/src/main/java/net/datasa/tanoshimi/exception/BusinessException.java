package net.datasa.tanoshimi.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) { super(errorCode.message()); this.errorCode = errorCode; }
    public BusinessException(ErrorCode errorCode, String message) { super(message); this.errorCode = errorCode; }
}

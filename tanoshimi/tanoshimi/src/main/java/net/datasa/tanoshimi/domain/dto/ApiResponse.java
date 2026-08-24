package net.datasa.tanoshimi.domain.dto;

public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, null, data); }
    public static <T> ApiResponse<T> ok(String message, T data) { return new ApiResponse<>(true, message, data); }
    /** 데이터 없이 메시지만 내려줄 때 전용 - ok(T data) 와 이름이 겹치면 String 인자에서 오버로드가
     *  모호해져 엉뚱한(Void) 타입으로 잘못 resolve 될 수 있어 이름을 분리했다. */
    public static ApiResponse<Void> okMessage(String message) { return new ApiResponse<>(true, message, null); }
    public static ApiResponse<Void> fail(String message) { return new ApiResponse<>(false, message, null); }
}

package com.redface.dto;

/**
 * C10 后台写操作统一返回对象，用于向前端展示操作结果和审计线索。
 */
public class AdminOperationResult<T> {
    private String action;
    private String message;
    private T result;

    public static <T> AdminOperationResult<T> of(String action, String message, T result) {
        AdminOperationResult<T> response = new AdminOperationResult<>();
        response.setAction(action);
        response.setMessage(message);
        response.setResult(result);
        return response;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getResult() { return result; }
    public void setResult(T result) { this.result = result; }
}

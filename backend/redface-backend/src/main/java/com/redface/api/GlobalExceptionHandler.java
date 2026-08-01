package com.redface.api;

import com.redface.service.LiveMetricEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * C9 全局异常处理器。API-3 的 40001~40005 业务码由 TokenController 显式映射，
 * 这里处理通用鉴权、参数和未知异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(40000, e.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(40000, e.getMessage(), null));
    }

    /**
     * 请求方法不支持。必须显式映射为 405，否则会被兜底 {@code Exception} 处理器
     * 吞成 500，使得客户端无法区分「接口不支持该方法」与「服务器出错」。
     * C20-5 展示端的只读约束依赖该语义：写操作必须得到明确的 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(40501, "不支持的请求方法: " + e.getMethod(), null));
    }

    /**
     * 静态资源或路径不存在。同样必须显式映射，否则被兜底处理器吞成 500。
     *
     * <p>C20-5 实测时暴露的既有缺陷：访问任意不存在的路径（包括目录形式的
     * {@code /display/}）都返回 500。这在现场会造成严重误导：运维看到 500
     * 会去查服务器或数据库，而实际只是网址敲错。修为标准 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, "请求的资源不存在", null));
    }

    /**
     * C20-4A 录入的当前总数小于水位线，需运营确认是否为新场次开播。
     *
     * <p>用 409 而非 400 表达：这不是「参数写错了」，而是「系统状态与提交内容不一致，
     * 需要人来判断」。此时<b>未写入任何数据</b>，人气值不受影响。
     * 响应 data 携带预演结果，供前端渲染确认弹窗时展示当前总数与上次记录的差异。
     */
    @ExceptionHandler(LiveMetricEntryService.WatermarkCalibrationRequiredException.class)
    public ResponseEntity<ApiResponse<Object>> handleCalibrationRequired(
            LiveMetricEntryService.WatermarkCalibrationRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(40910, e.getMessage(), e.getPreview()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        // 兜底处理器必须打日志：否则任何未预期异常都只留下一句「服务器内部错误」，
        // 现场无法定位。原实现完全丢弃了异常堆栈。
        log.error("未处理异常：{}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "服务器内部错误", null));
    }
}

package com.shiliuai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {
    private final ShiliuProperties properties;

    public AdminTokenInterceptor(ShiliuProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expectedToken = properties.getAdminToken();
        if (!StringUtils.hasText(expectedToken)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/health".equals(request.getRequestURI())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (("Bearer " + expectedToken).equals(authorization)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"缺少或错误的 Admin Token\"}");
        return false;
    }
}

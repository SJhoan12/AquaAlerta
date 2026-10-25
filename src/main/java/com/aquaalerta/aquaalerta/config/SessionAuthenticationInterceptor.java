package com.aquaalerta.aquaalerta.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

public class SessionAuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String requestUri = request.getRequestURI();
        String cleanUri = requestUri.replaceAll("(?i);jsessionid=[^/;]*", "");
        if (!cleanUri.equals(requestUri)) {
            String query = request.getQueryString();
            response.sendRedirect(cleanUri + (query == null ? "" : "?" + query));
            return false;
        }

        String path = requestUri.substring(request.getContextPath().length());
        if (isPublicPath(request, path)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("usuario") != null) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }

    private boolean isPublicPath(HttpServletRequest request, String path) {
        return path.equals("/login")
                || path.equals("/registro")
                || path.equals("/style.css")
                || path.equals("/favicon.ico")
                || path.startsWith("/webjars/")
                || (path.equals("/api/lecturas") && "POST".equalsIgnoreCase(request.getMethod()));
    }
}

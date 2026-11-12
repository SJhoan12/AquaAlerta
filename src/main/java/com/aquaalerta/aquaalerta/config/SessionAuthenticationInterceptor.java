package com.aquaalerta.aquaalerta.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class SessionAuthenticationInterceptor implements HandlerInterceptor {

    private static final String CSRF_SESSION_ATTRIBUTE = "csrfToken";

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
        if (path.equals("/api/lecturas") && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (path.equals("/mfa") || path.startsWith("/mfa/")) {
            HttpSession pendingSession = request.getSession(false);
            if (pendingSession == null
                    || pendingSession.getAttribute("mfaPendingUserId") == null) {
                response.sendRedirect(request.getContextPath() + "/login");
                return false;
            }
            ensureCsrfToken(pendingSession);
            return isSafeMethod(request.getMethod()) || validateCsrf(request, response);
        }

        if (isPublicPath(path)) {
            if (isSafeMethod(request.getMethod())) {
                if (isAuthenticationPage(path)) {
                    ensureCsrfToken(request.getSession(true));
                }
                return true;
            }
            return validateCsrf(request, response);
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuario") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        String role = (String) session.getAttribute("rol");
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (requiresAdmin(path) && !"ADMIN".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        ensureCsrfToken(session);
        return isSafeMethod(request.getMethod()) || validateCsrf(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.equals("/login")
                || path.equals("/registro")
                || path.equals("/style.css")
                || path.equals("/theme.css")
                || path.equals("/theme.js")
                || path.equals("/favicon.ico")
                || path.startsWith("/webjars/");
    }

    private boolean isAuthenticationPage(String path) {
        return path.equals("/login") || path.equals("/registro");
    }

    private boolean requiresAdmin(String path) {
        return path.equals("/tanques")
                || path.startsWith("/tanques/")
                || path.startsWith("/alertas/resolver/");
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)
                || "TRACE".equalsIgnoreCase(method);
    }

    private boolean validateCsrf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        String expected = session == null
                ? null
                : (String) session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        String provided = request.getParameter("_csrf");
        if (expected == null || provided == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    private void ensureCsrfToken(HttpSession session) {
        if (session.getAttribute(CSRF_SESSION_ATTRIBUTE) == null) {
            session.setAttribute(CSRF_SESSION_ATTRIBUTE, UUID.randomUUID().toString());
        }
    }
}

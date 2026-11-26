package com.aquaalerta.aquaalerta.config;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAuthenticationInterceptorTests {

    private final SessionAuthenticationInterceptor interceptor = new SessionAuthenticationInterceptor();

    @Test
    void redirectsUnauthenticatedPageRequestsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/historial");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals("/login", response.getRedirectedUrl());
    }

    @Test
    void removesSessionIdsFromLegacyUrls() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/;jsessionid=old-session-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    void permitsRequestsWithAnAuthenticatedSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/historial");
        HttpSession session = request.getSession();
        session.setAttribute("usuario", "estudiante");
        session.setAttribute("rol", "USER");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertTrue(session.getAttribute("csrfToken") instanceof String);
    }

    @Test
    void keepsLoginAndRegistrationPagesPublicAndSensorSubmissionAvailableForApiAuthentication()
            throws Exception {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("GET", "/login");
        MockHttpServletRequest registrationRequest = new MockHttpServletRequest("GET", "/registro");
        MockHttpServletRequest sensorRequest = new MockHttpServletRequest("POST", "/api/lecturas");

        assertTrue(interceptor.preHandle(loginRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(registrationRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(sensorRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(loginRequest.getSession().getAttribute("csrfToken") instanceof String);
        assertTrue(registrationRequest.getSession().getAttribute("csrfToken") instanceof String);
    }

    @Test
    void rejectsAdminRoutesForRegularUsers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tanques");
        request.getSession().setAttribute("usuario", "estudiante");
        request.getSession().setAttribute("rol", "USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void acceptsAdminRoutesForAdministrators() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tanques");
        request.getSession().setAttribute("usuario", "admin");
        request.getSession().setAttribute("rol", "ADMIN");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void requiresCsrfTokenForBrowserWrites() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tanques/guardar");
        request.getSession().setAttribute("usuario", "admin");
        request.getSession().setAttribute("rol", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void acceptsBrowserWriteWithMatchingCsrfToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tanques/guardar");
        HttpSession session = request.getSession();
        session.setAttribute("usuario", "admin");
        session.setAttribute("rol", "ADMIN");
        session.setAttribute("csrfToken", "valid-token");
        request.addParameter("_csrf", "valid-token");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void allowsUserToToggleVigilanceWithCsrfWhileKeepingTankSettingsAdminOnly() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/tanques/tanque-01/modo-vigilancia/toggle");
        HttpSession session = request.getSession();
        session.setAttribute("usuario", "estudiante");
        session.setAttribute("rol", "USER");
        session.setAttribute("csrfToken", "valid-token");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, rejected, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, rejected.getStatus());

        request.addParameter("_csrf", "valid-token");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void requiresCsrfTokenForLoginSubmission() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void requiresPasswordAuthenticatedPendingSessionForMfaAndProtectsMfaWritesWithCsrf()
            throws Exception {
        MockHttpServletRequest unauthenticated = new MockHttpServletRequest("GET", "/mfa/setup");
        MockHttpServletResponse redirect = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(unauthenticated, redirect, new Object()));
        assertEquals("/login", redirect.getRedirectedUrl());

        MockHttpServletRequest pending = new MockHttpServletRequest("POST", "/mfa/verify");
        HttpSession session = pending.getSession();
        session.setAttribute("mfaPendingUserId", "user-id");
        session.setAttribute("csrfToken", "valid-token");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pending, rejected, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, rejected.getStatus());

        pending.addParameter("_csrf", "valid-token");
        assertTrue(interceptor.preHandle(pending, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void requiresAuthenticationToReadSensorData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lecturas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals("/login", response.getRedirectedUrl());
    }

    @Test
    void keepsStylesheetPublicForLoginAndRegistrationPages() throws Exception {
        for (String path : new String[]{
                "/style.css", "/theme.css", "/theme.js", "/favicon.ico", "/webjars/bootstrap.css"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()), path);
        }
    }
}

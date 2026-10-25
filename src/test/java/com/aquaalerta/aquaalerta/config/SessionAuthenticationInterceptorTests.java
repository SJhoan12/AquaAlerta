package com.aquaalerta.aquaalerta.config;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
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

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void keepsLoginRegistrationAndSensorSubmissionPublic() throws Exception {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("GET", "/login");
        MockHttpServletRequest registrationRequest = new MockHttpServletRequest("GET", "/registro");
        MockHttpServletRequest sensorRequest = new MockHttpServletRequest("POST", "/api/lecturas");

        assertTrue(interceptor.preHandle(loginRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(registrationRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(sensorRequest, new MockHttpServletResponse(), new Object()));
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
        for (String path : new String[]{"/style.css", "/favicon.ico", "/webjars/bootstrap.css"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()), path);
        }
    }
}

package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Usuario;
import com.aquaalerta.aquaalerta.repository.UsuarioRepository;
import com.aquaalerta.aquaalerta.service.PasswordService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerTests {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private final HomeController controller = new HomeController(usuarioRepository, passwordService);

    @Test
    void registrationAlwaysCreatesUserRoleEvenWhenAdminRoleIsSubmitted() {
        Usuario submitted = new Usuario();
        submitted.setUsername("estudiante");
        submitted.setPassword("password");
        submitted.setRol("ADMIN");
        submitted.setId("existing-admin-id");
        submitted.setTotpEnabled(true);
        submitted.setTotpSecretEncrypted("forged-secret");
        when(usuarioRepository.findByUsername("estudiante")).thenReturn(Optional.empty());
        when(passwordService.encode("password")).thenReturn("bcrypt-hash");

        String result = controller.procesarRegistro(submitted, new ExtendedModelMap());

        assertEquals("redirect:/login?registrado=ok", result);
        verify(usuarioRepository).save(any(Usuario.class));
        assertEquals("USER", submitted.getRol());
        assertNull(submitted.getId());
        assertEquals("bcrypt-hash", submitted.getPassword());
        assertFalse(submitted.isTotpEnabled());
        assertNull(submitted.getTotpSecretEncrypted());
    }

    @Test
    void loginRequiresAdminToEnrollInSecondFactorBeforeCreatingAuthenticatedSession() {
        Usuario admin = new Usuario();
        admin.setId("admin-id");
        admin.setUsername("admin");
        admin.setRol("ADMIN");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordService.verifyAndUpgrade(admin, "password")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        String result = controller.procesarLogin("admin", "password", request, new ExtendedModelMap());

        assertEquals("redirect:/mfa/setup", result);
        HttpSession session = request.getSession(false);
        assertNotNull(session);
        assertEquals("admin-id", session.getAttribute("mfaPendingUserId"));
        assertNull(session.getAttribute("usuario"));
        assertNull(session.getAttribute("rol"));
        assertNotNull(session.getAttribute("csrfToken"));
    }

    @Test
    void loginWithConfiguredSecondFactorRequiresCodeBeforeAuthentication() {
        Usuario usuario = new Usuario();
        usuario.setId("user-id");
        usuario.setUsername("usuario");
        usuario.setRol("USER");
        usuario.setTotpEnabled(true);
        usuario.setTotpSecretEncrypted("encrypted-secret");
        when(usuarioRepository.findByUsername("usuario")).thenReturn(Optional.of(usuario));
        when(passwordService.verifyAndUpgrade(usuario, "password")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        String result = controller.procesarLogin("usuario", "password", request, new ExtendedModelMap());

        assertEquals("redirect:/mfa/verify", result);
        assertNull(request.getSession(false).getAttribute("usuario"));
    }
}

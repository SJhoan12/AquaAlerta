package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Usuario;
import com.aquaalerta.aquaalerta.repository.UsuarioRepository;
import com.aquaalerta.aquaalerta.service.TotpSecretEncryptionService;
import com.aquaalerta.aquaalerta.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MfaControllerTests {

    private static final String ENCRYPTION_KEY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final TotpService totpService = new TotpService();
    private final TotpSecretEncryptionService encryptionService =
            new TotpSecretEncryptionService(ENCRYPTION_KEY);
    private final MfaController controller =
            new MfaController(usuarioRepository, totpService, encryptionService);

    @Test
    void requiresValidTotpCodeToFinishEnrollmentAndLogin() {
        Usuario usuario = usuario("admin", "ADMIN");
        when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(usuario));
        MockHttpSession session = pendingSession();
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("mfa-setup", controller.mostrarConfiguracion(session, model));
        String secret = (String) session.getAttribute("mfaPendingTotpSecret");
        String code = currentCode(secret);
        assertEquals("redirect:/",
                controller.confirmarConfiguracion(code, session, model));

        assertTrue(usuario.isTotpEnabled());
        assertNotNull(usuario.getTotpSecretEncrypted());
        assertNotEquals(secret, usuario.getTotpSecretEncrypted());
        assertEquals(secret, encryptionService.decrypt(usuario.getTotpSecretEncrypted()));
        assertNotNull(usuario.getUltimoCodigoTotpHash());
        assertNotEquals(code, usuario.getUltimoCodigoTotpHash());
        assertEquals("admin", session.getAttribute("usuario"));
        assertEquals("ADMIN", session.getAttribute("rol"));
        assertNull(session.getAttribute("mfaPendingUserId"));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void doesNotAuthenticateWhenEnrollmentCodeIsWrong() {
        Usuario usuario = usuario("usuario", "USER");
        when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(usuario));
        MockHttpSession session = pendingSession();
        ExtendedModelMap model = new ExtendedModelMap();
        controller.mostrarConfiguracion(session, model);

        assertEquals("mfa-setup",
                controller.confirmarConfiguracion("000000", session, model));
        assertNull(session.getAttribute("usuario"));
        assertEquals("El código no es válido. Inténtalo de nuevo.", model.get("error"));
    }

    @Test
    void verifiesSecondFactorBeforeRestoringExistingUserSession() {
        Usuario usuario = usuario("usuario", "USER");
        String secret = totpService.generarSecreto();
        usuario.setTotpEnabled(true);
        usuario.setTotpSecretEncrypted(encryptionService.encrypt(secret));
        when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(usuario));
        MockHttpSession session = pendingSession();

        assertEquals("mfa-verify", controller.mostrarVerificacion(session));
        assertEquals("redirect:/",
                controller.verificarCodigo(currentCode(secret), session, new ExtendedModelMap()));

        assertEquals("usuario", session.getAttribute("usuario"));
        assertEquals("USER", session.getAttribute("rol"));
    }

    @Test
    void rejectsARecentlyAcceptedTotpCodeOnAnotherLogin() {
        Usuario usuario = usuario("usuario", "USER");
        String secret = totpService.generarSecreto();
        String code = currentCode(secret);
        usuario.setTotpEnabled(true);
        usuario.setTotpSecretEncrypted(encryptionService.encrypt(secret));
        usuario.setUltimoCodigoTotpHash(totpService.hashCodigo(code));
        usuario.setUltimoCodigoTotpAceptado(LocalDateTime.now());
        when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(usuario));
        MockHttpSession session = pendingSession();
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("mfa-verify",
                controller.verificarCodigo(code, session, model));
        assertNull(session.getAttribute("usuario"));
        assertEquals("El código no es válido. Inténtalo de nuevo.", model.get("error"));
    }

    private MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("mfaPendingUserId", "user-id");
        session.setAttribute("csrfToken", "csrf");
        return session;
    }

    private Usuario usuario(String username, String role) {
        Usuario usuario = new Usuario();
        usuario.setId("user-id");
        usuario.setUsername(username);
        usuario.setRol(role);
        return usuario;
    }

    private String currentCode(String secret) {
        int code = new GoogleAuthenticator().getTotpPassword(secret);
        return String.format("%06d", code);
    }
}

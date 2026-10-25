package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTests {

    private final PasswordService passwordService =
            new PasswordService(new BCryptPasswordEncoder());

    @Test
    void encodesNewPasswordsAndVerifiesThem() {
        String encoded = passwordService.encode("agua-segura-123");
        Usuario usuario = usuarioConPassword(encoded);

        assertNotEquals("agua-segura-123", encoded);
        assertTrue(passwordService.verifyAndUpgrade(usuario, "agua-segura-123"));
        assertFalse(passwordService.verifyAndUpgrade(usuario, "incorrecta"));
    }

    @Test
    void upgradesLegacyPlainTextPasswordAfterSuccessfulLogin() {
        Usuario usuario = usuarioConPassword("clave-anterior");

        assertTrue(passwordService.verifyAndUpgrade(usuario, "clave-anterior"));
        assertNotEquals("clave-anterior", usuario.getPassword());
        assertTrue(passwordService.verifyAndUpgrade(usuario, "clave-anterior"));
    }

    @Test
    void doesNotUpgradeLegacyPasswordWhenItDoesNotMatch() {
        Usuario usuario = usuarioConPassword("clave-anterior");

        assertFalse(passwordService.verifyAndUpgrade(usuario, "clave-incorrecta"));
        assertTrue("clave-anterior".equals(usuario.getPassword()));
    }

    private Usuario usuarioConPassword(String password) {
        Usuario usuario = new Usuario();
        usuario.setPassword(password);
        return usuario;
    }
}

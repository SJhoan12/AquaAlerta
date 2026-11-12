package com.aquaalerta.aquaalerta.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTests {

    private final TotpService service = new TotpService();

    @Test
    void verifiesStandardSixDigitTotpCodes() {
        String secret = service.generarSecreto();
        int currentCode = new GoogleAuthenticator().getTotpPassword(secret);

        assertTrue(service.verificar(secret, String.format("%06d", currentCode)));
    }

    @Test
    void rejectsMalformedCodes() {
        assertFalse(service.verificar("secret", "12"));
        assertFalse(service.verificar("secret", "abcdef"));
    }

    @Test
    void detectsRecentlyReusedCodesWithoutStoringTheCodeItself() {
        String code = "123456";
        String hash = service.hashCodigo(code);

        assertTrue(service.fueUsadoRecientemente(code, hash, LocalDateTime.now()));
        assertFalse(service.fueUsadoRecientemente(
                code, hash, LocalDateTime.now().minusMinutes(3)));
        assertNotEquals(code, hash);
    }
}

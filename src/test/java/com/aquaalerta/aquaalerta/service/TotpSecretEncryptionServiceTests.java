package com.aquaalerta.aquaalerta.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TotpSecretEncryptionServiceTests {

    private static final String ENCRYPTION_KEY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void encryptsSecretsWithRandomizedAuthenticatedCiphertext() {
        TotpSecretEncryptionService service = new TotpSecretEncryptionService(ENCRYPTION_KEY);

        String first = service.encrypt("TOTP-SECRET");
        String second = service.encrypt("TOTP-SECRET");

        assertNotEquals("TOTP-SECRET", first);
        assertNotEquals(first, second);
        assertEquals("TOTP-SECRET", service.decrypt(first));
    }

    @Test
    void failsClearlyWhenEncryptionKeyIsMissingOrInvalid() {
        TotpSecretEncryptionService service = new TotpSecretEncryptionService("");

        assertThrows(IllegalStateException.class, () -> service.encrypt("TOTP-SECRET"));
    }

    @Test
    void rejectsTamperedCiphertext() {
        TotpSecretEncryptionService service = new TotpSecretEncryptionService(ENCRYPTION_KEY);
        String encrypted = service.encrypt("TOTP-SECRET");
        char replacement = encrypted.charAt(encrypted.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }
}

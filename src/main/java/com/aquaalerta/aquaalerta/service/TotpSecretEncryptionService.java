package com.aquaalerta.aquaalerta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TotpSecretEncryptionService {

    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encryptionKeyHex;

    public TotpSecretEncryptionService(
            @Value("${aquaalerta.totp.encryption-key:}") String encryptionKeyHex) {
        this.encryptionKeyHex = encryptionKeyHex;
    }

    public String encrypt(String secret) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP.", exception);
        }
    }

    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || !encryptedSecret.startsWith(PREFIX)) {
            throw new IllegalStateException("El secreto TOTP almacenado no tiene un formato compatible.");
        }

        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(encryptedSecret.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("El secreto TOTP almacenado no es válido.", exception);
        }
        if (packed.length <= NONCE_LENGTH_BYTES) {
            throw new IllegalStateException("El secreto TOTP almacenado está incompleto.");
        }

        byte[] nonce = Arrays.copyOfRange(packed, 0, NONCE_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(packed, NONCE_LENGTH_BYTES, packed.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "No se pudo descifrar el secreto TOTP; verifica la clave de cifrado configurada.",
                    exception);
        }
    }

    private SecretKeySpec key() {
        if (encryptionKeyHex == null || !encryptionKeyHex.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException(
                    "Configura AQUAALERTA_TOTP_ENCRYPTION_KEY con 32 bytes aleatorios en hexadecimal.");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(encryptionKeyHex), "AES");
    }
}

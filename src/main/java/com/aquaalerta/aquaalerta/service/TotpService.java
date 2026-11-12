package com.aquaalerta.aquaalerta.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class TotpService {

    private static final Duration REPLAY_WINDOW = Duration.ofMinutes(2);
    private final GoogleAuthenticator authenticator = new GoogleAuthenticator();

    public String generarSecreto() {
        GoogleAuthenticatorKey credentials = authenticator.createCredentials();
        return credentials.getKey();
    }

    public boolean verificar(String secreto, String codigo) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        return authenticator.authorize(secreto, Integer.parseInt(codigo));
    }

    public boolean fueUsadoRecientemente(String codigo,
                                         String hashAnterior,
                                         LocalDateTime fechaAceptacion) {
        if (hashAnterior == null || fechaAceptacion == null
                || fechaAceptacion.isBefore(LocalDateTime.now().minus(REPLAY_WINDOW))) {
            return false;
        }
        byte[] hashCodigo = hashCodigo(codigo).getBytes(StandardCharsets.US_ASCII);
        byte[] hashGuardado = hashAnterior.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(hashCodigo, hashGuardado);
    }

    public String hashCodigo(String codigo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            codigo.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo proteger el código TOTP usado.", exception);
        }
    }
}

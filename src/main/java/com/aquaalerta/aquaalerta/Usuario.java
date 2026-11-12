package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;
    private String username;
    private String password;
    private String email;
    private String rol;
    private String totpSecretEncrypted;
    private boolean totpEnabled;
    private String ultimoCodigoTotpHash;
    private LocalDateTime ultimoCodigoTotpAceptado;

    public Usuario() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getTotpSecretEncrypted() {
        return totpSecretEncrypted;
    }

    public void setTotpSecretEncrypted(String totpSecretEncrypted) {
        this.totpSecretEncrypted = totpSecretEncrypted;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public String getUltimoCodigoTotpHash() {
        return ultimoCodigoTotpHash;
    }

    public void setUltimoCodigoTotpHash(String ultimoCodigoTotpHash) {
        this.ultimoCodigoTotpHash = ultimoCodigoTotpHash;
    }

    public LocalDateTime getUltimoCodigoTotpAceptado() {
        return ultimoCodigoTotpAceptado;
    }

    public void setUltimoCodigoTotpAceptado(LocalDateTime ultimoCodigoTotpAceptado) {
        this.ultimoCodigoTotpAceptado = ultimoCodigoTotpAceptado;
    }
}

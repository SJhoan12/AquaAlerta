package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordService {

    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean verifyAndUpgrade(Usuario usuario, String rawPassword) {
        String storedPassword = usuario.getPassword();
        if (storedPassword == null || rawPassword == null) {
            return false;
        }

        if (BCRYPT_HASH.matcher(storedPassword).matches()) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        if (!storedPassword.equals(rawPassword)) {
            return false;
        }

        usuario.setPassword(passwordEncoder.encode(rawPassword));
        return true;
    }
}

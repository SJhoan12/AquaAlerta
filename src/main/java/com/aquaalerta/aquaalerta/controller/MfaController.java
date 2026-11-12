package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Usuario;
import com.aquaalerta.aquaalerta.repository.UsuarioRepository;
import com.aquaalerta.aquaalerta.service.TotpSecretEncryptionService;
import com.aquaalerta.aquaalerta.service.TotpService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/mfa")
public class MfaController {

    private static final String PENDING_USER_ID = "mfaPendingUserId";
    private static final String PENDING_SECRET = "mfaPendingTotpSecret";
    private static final String CSRF_TOKEN = "csrfToken";
    private static final int MAX_INTENTOS = 5;

    private final UsuarioRepository usuarioRepository;
    private final TotpService totpService;
    private final TotpSecretEncryptionService encryptionService;

    public MfaController(UsuarioRepository usuarioRepository,
                         TotpService totpService,
                         TotpSecretEncryptionService encryptionService) {
        this.usuarioRepository = usuarioRepository;
        this.totpService = totpService;
        this.encryptionService = encryptionService;
    }

    @GetMapping("/setup")
    public String mostrarConfiguracion(HttpSession session, Model model) {
        Optional<Usuario> usuario = usuarioPendiente(session);
        if (usuario.isEmpty()) {
            return terminarSesionPendiente(session);
        }
        if (tieneSecretoActivo(usuario.get())) {
            return "redirect:/mfa/verify";
        }
        agregarModeloConfiguracion(session, model, null);
        return "mfa-setup";
    }

    @PostMapping("/setup")
    public String confirmarConfiguracion(@RequestParam String codigo,
                                         HttpSession session,
                                         Model model) {
        Optional<Usuario> usuario = usuarioPendiente(session);
        if (usuario.isEmpty()) {
            return terminarSesionPendiente(session);
        }
        String secreto = (String) session.getAttribute(PENDING_SECRET);
        if (secreto == null) {
            return "redirect:/mfa/setup";
        }
        if (!codigoValidoYNoReutilizado(secreto, codigo, usuario.get())) {
            if (registrarIntentoFallido(session)) {
                return "redirect:/login?mfa=expired";
            }
            agregarModeloConfiguracion(session, model, "El código no es válido. Inténtalo de nuevo.");
            return "mfa-setup";
        }

        String secretoCifrado;
        try {
            secretoCifrado = encryptionService.encrypt(secreto);
        } catch (IllegalStateException exception) {
            agregarModeloConfiguracion(session, model, exception.getMessage());
            return "mfa-setup";
        }
        Usuario cuenta = usuario.get();
        cuenta.setTotpSecretEncrypted(secretoCifrado);
        cuenta.setTotpEnabled(true);
        registrarCodigoUsado(cuenta, codigo);
        usuarioRepository.save(cuenta);
        completarAutenticacion(cuenta, session);
        return "redirect:/";
    }

    @GetMapping("/verify")
    public String mostrarVerificacion(HttpSession session) {
        Optional<Usuario> usuario = usuarioPendiente(session);
        if (usuario.isEmpty()) {
            return terminarSesionPendiente(session);
        }
        return tieneSecretoActivo(usuario.get()) ? "mfa-verify" : "redirect:/mfa/setup";
    }

    @PostMapping("/verify")
    public String verificarCodigo(@RequestParam String codigo,
                                  HttpSession session,
                                  Model model) {
        Optional<Usuario> usuario = usuarioPendiente(session);
        if (usuario.isEmpty()) {
            return terminarSesionPendiente(session);
        }
        Usuario cuenta = usuario.get();
        if (!tieneSecretoActivo(cuenta)) {
            return "redirect:/mfa/setup";
        }

        String secreto;
        try {
            secreto = encryptionService.decrypt(cuenta.getTotpSecretEncrypted());
        } catch (IllegalStateException exception) {
            model.addAttribute("error", exception.getMessage());
            return "mfa-verify";
        }
        if (!codigoValidoYNoReutilizado(secreto, codigo, cuenta)) {
            if (registrarIntentoFallido(session)) {
                return "redirect:/login?mfa=expired";
            }
            model.addAttribute("error", "El código no es válido. Inténtalo de nuevo.");
            return "mfa-verify";
        }
        registrarCodigoUsado(cuenta, codigo);
        usuarioRepository.save(cuenta);
        completarAutenticacion(cuenta, session);
        return "redirect:/";
    }

    private void agregarModeloConfiguracion(HttpSession session, Model model, String error) {
        String secreto = (String) session.getAttribute(PENDING_SECRET);
        if (secreto == null) {
            secreto = totpService.generarSecreto();
            session.setAttribute(PENDING_SECRET, secreto);
        }
        model.addAttribute("secreto", secreto);
        if (error != null) {
            model.addAttribute("error", error);
        }
    }

    private Optional<Usuario> usuarioPendiente(HttpSession session) {
        String userId = (String) session.getAttribute(PENDING_USER_ID);
        return userId == null ? Optional.empty() : usuarioRepository.findById(userId);
    }

    private boolean tieneSecretoActivo(Usuario usuario) {
        return usuario.isTotpEnabled()
                && usuario.getTotpSecretEncrypted() != null
                && !usuario.getTotpSecretEncrypted().isBlank();
    }

    private boolean codigoValidoYNoReutilizado(String secreto, String codigo, Usuario usuario) {
        return totpService.verificar(secreto, codigo)
                && !totpService.fueUsadoRecientemente(
                        codigo, usuario.getUltimoCodigoTotpHash(),
                        usuario.getUltimoCodigoTotpAceptado());
    }

    private void registrarCodigoUsado(Usuario usuario, String codigo) {
        usuario.setUltimoCodigoTotpHash(totpService.hashCodigo(codigo));
        usuario.setUltimoCodigoTotpAceptado(LocalDateTime.now());
    }

    private boolean registrarIntentoFallido(HttpSession session) {
        int intentos = session.getAttribute("mfaAttempts") instanceof Integer count ? count : 0;
        intentos++;
        if (intentos >= MAX_INTENTOS) {
            session.invalidate();
            return true;
        }
        session.setAttribute("mfaAttempts", intentos);
        return false;
    }

    private void completarAutenticacion(Usuario usuario, HttpSession session) {
        session.removeAttribute(PENDING_USER_ID);
        session.removeAttribute(PENDING_SECRET);
        session.removeAttribute("mfaAttempts");
        session.setAttribute("usuario", usuario.getUsername());
        session.setAttribute("rol", "ADMIN".equals(usuario.getRol()) ? "ADMIN" : "USER");
        session.setAttribute(CSRF_TOKEN, UUID.randomUUID().toString());
    }

    private String terminarSesionPendiente(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

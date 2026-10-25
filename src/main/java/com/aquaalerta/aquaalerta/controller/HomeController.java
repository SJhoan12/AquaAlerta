package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Usuario;
import com.aquaalerta.aquaalerta.repository.UsuarioRepository;
import com.aquaalerta.aquaalerta.service.PasswordService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;

    public HomeController(UsuarioRepository usuarioRepository, PasswordService passwordService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordService = passwordService;
    }

    @GetMapping("/login")
    public String mostrarLogin(@RequestParam(required = false) String registrado,
                               Model model) {
        if ("ok".equals(registrado)) {
            model.addAttribute("mensaje", "Cuenta creada. Ahora inicia sesión.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String username,
                                @RequestParam String password,
                                HttpServletRequest request,
                                Model model) {
        var opt = usuarioRepository.findByUsername(username);
        if (opt.isPresent() && passwordService.verifyAndUpgrade(opt.get(), password)) {
            usuarioRepository.save(opt.get());
            HttpSession previousSession = request.getSession(false);
            if (previousSession != null) {
                previousSession.invalidate();
            }
            request.getSession(true).setAttribute("usuario", username);
            return "redirect:/";
        }
        model.addAttribute("error", "Usuario o contraseña incorrectos.");
        return "login";
    }

    @GetMapping("/registro")
    public String mostrarRegistro(Model model) {
        if (!model.containsAttribute("usuario")) {
            Usuario u = new Usuario();
            u.setRol("USER");
            model.addAttribute("usuario", u);
        }
        return "registro";
    }

    @PostMapping("/registro")
    public String procesarRegistro(@ModelAttribute Usuario usuario, Model model) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()
                || usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            model.addAttribute("error", "Completa usuario y contraseña.");
            model.addAttribute("usuario", usuario);
            return "registro";
        }
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            model.addAttribute("error", "Ese usuario ya existe. Elige otro.");
            model.addAttribute("usuario", usuario);
            return "registro";
        }
        usuario.setPassword(passwordService.encode(usuario.getPassword()));
        if (usuario.getRol() == null || usuario.getRol().isBlank()) {
            usuario.setRol("USER");
        }
        usuarioRepository.save(usuario);
        return "redirect:/login?registrado=ok";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

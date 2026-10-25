package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Usuario;
import com.aquaalerta.aquaalerta.repository.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final UsuarioRepository usuarioRepository;

    public HomeController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
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
                                HttpSession session,
                                Model model) {
        var opt = usuarioRepository.findByUsername(username);
        if (opt.isPresent() && opt.get().getPassword().equals(password)) {
            session.setAttribute("usuario", username);
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


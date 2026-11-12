package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.service.TanqueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tanques")
public class TanqueController {

    private final TanqueService tanqueService;
    private final AlertaRepository alertaRepository;

    public TanqueController(TanqueService tanqueService,
                            AlertaRepository alertaRepository) {
        this.tanqueService = tanqueService;
        this.alertaRepository = alertaRepository;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("tanques", tanqueService.listarTodos());
        if (!model.containsAttribute("tanque")) {
            model.addAttribute("tanque", new Tanque());
        }
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "tanques";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Tanque tanque, RedirectAttributes redirectAttributes) {
        try {
            tanqueService.guardar(tanque);
            redirectAttributes.addFlashAttribute("mensajeTanque", "Se guardó la configuración del tanque.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorTanque", exception.getMessage());
            redirectAttributes.addFlashAttribute("tanque", tanque);
        }
        return "redirect:/tanques";
    }

    @PostMapping("/{id}/registrar-lleno")
    public String registrarLleno(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            tanqueService.registrarLleno(id);
            redirectAttributes.addFlashAttribute("mensajeTanque",
                    "Listo. Se guardó la lectura actual como referencia de tanque lleno.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorTanque", exception.getMessage());
        }
        return "redirect:/tanques";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("tanques", tanqueService.listarTodos());
        model.addAttribute("tanque",
                tanqueService.buscarPorId(id).orElse(new Tanque()));
        return "tanques";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable String id) {
        tanqueService.eliminarPorId(id);
        return "redirect:/tanques";
    }
}

package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.service.TanqueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
    public String guardar(@ModelAttribute Tanque tanque) {
        tanqueService.guardar(tanque);
        return "redirect:/tanques";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("tanques", tanqueService.listarTodos());
        model.addAttribute("tanque",
                tanqueService.buscarPorId(id).orElse(new Tanque()));
        return "tanques";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable String id) {
        tanqueService.eliminarPorId(id);
        return "redirect:/tanques";
    }
}


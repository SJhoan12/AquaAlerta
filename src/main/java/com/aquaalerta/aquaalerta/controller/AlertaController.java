package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/alertas")
public class AlertaController {

    private final AlertaRepository alertaRepository;

    public AlertaController(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @GetMapping
    public String bandeja(Model model) {
        model.addAttribute("activas", alertaRepository.findByEstado("ACTIVA"));
        model.addAttribute("resueltas", alertaRepository.findByEstado("RESUELTA"));
        return "alertas";
    }

    @PostMapping("/resolver/{id}")
    public String resolver(@PathVariable String id) {
        alertaRepository.findById(id).ifPresent(a -> {
            a.setEstado("RESUELTA");
            a.setResuelta(true);
            alertaRepository.save(a);
        });
        return "redirect:/alertas";
    }
}


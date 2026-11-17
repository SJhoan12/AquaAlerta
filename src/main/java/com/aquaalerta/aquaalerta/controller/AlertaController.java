package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Alerta;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/alertas")
public class AlertaController {

    private final AlertaRepository alertaRepository;

    public AlertaController(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @GetMapping
    public String bandeja(Model model) {
        List<Alerta> activas = alertaRepository.findByEstado("ACTIVA");
        model.addAttribute("activas", activas);
        model.addAttribute("resueltas", alertaRepository.findByEstado("RESUELTA"));
        model.addAttribute("numAlertas", activas.size());
        model.addAttribute("cantidadInformativos", activas.stream()
                .filter(alerta -> "INFORMATIVO".equals(alerta.getTipo()))
                .count());
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


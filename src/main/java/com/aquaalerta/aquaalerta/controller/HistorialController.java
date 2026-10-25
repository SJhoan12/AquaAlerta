package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/historial")
public class HistorialController {

    private final LecturaNivelRepository lecturaNivelRepository;

    public HistorialController(LecturaNivelRepository lecturaNivelRepository) {
        this.lecturaNivelRepository = lecturaNivelRepository;
    }

    @GetMapping
    public String historial(@RequestParam(required = false) String tanqueId, Model model) {
        List<LecturaNivel> lecturas = (tanqueId == null || tanqueId.isBlank())
                ? lecturaNivelRepository.findAll()
                : lecturaNivelRepository.findByTanqueIdOrderByTimestampDesc(tanqueId);
        lecturas = lecturas.stream()
                .sorted(Comparator.comparing(LecturaNivel::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        model.addAttribute("lecturas", lecturas);
        model.addAttribute("tanqueId", tanqueId == null ? "" : tanqueId);
        return "historial";
    }
}


package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;

@Controller
@RequestMapping("/historial")
public class HistorialController {

    private final LecturaNivelRepository lecturaNivelRepository;
    private final AlertaRepository alertaRepository;
    private final TanqueRepository tanqueRepository;

    public HistorialController(LecturaNivelRepository lecturaNivelRepository,
                               AlertaRepository alertaRepository,
                               TanqueRepository tanqueRepository) {
        this.lecturaNivelRepository = lecturaNivelRepository;
        this.alertaRepository = alertaRepository;
        this.tanqueRepository = tanqueRepository;
    }

    @GetMapping
    public String historial(@RequestParam(required = false) String tanqueId,
                            @RequestParam(required = false) LocalDate desde,
                            @RequestParam(required = false) LocalDate hasta,
                            Model model) {
        List<LecturaNivel> lecturas = (tanqueId == null || tanqueId.isBlank())
                ? lecturaNivelRepository.findAll()
                : lecturaNivelRepository.findByTanqueIdOrderByTimestampDesc(tanqueId);
        lecturas = lecturas.stream()
                .filter(lectura -> desde == null || (lectura.getTimestamp() != null
                        && !lectura.getTimestamp().toLocalDate().isBefore(desde)))
                .filter(lectura -> hasta == null || (lectura.getTimestamp() != null
                        && !lectura.getTimestamp().toLocalDate().isAfter(hasta)))
                .sorted(Comparator.comparing(LecturaNivel::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        model.addAttribute("lecturas", lecturas);
        model.addAttribute("tanqueId", tanqueId == null ? "" : tanqueId);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tanques", tanqueRepository.findAll());
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "historial";
    }
}

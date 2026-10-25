package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final LecturaNivelRepository lecturaNivelRepository;
    private final AlertaRepository alertaRepository;

    public DashboardController(DashboardService dashboardService,
                               LecturaNivelRepository lecturaNivelRepository,
                               AlertaRepository alertaRepository) {
        this.dashboardService = dashboardService;
        this.lecturaNivelRepository = lecturaNivelRepository;
        this.alertaRepository = alertaRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String panel(@RequestParam(required = false) String tanqueId, Model model) {
        var estados = dashboardService.estadosTanques();
        var sel = estados.stream()
                .filter(e -> tanqueId == null || tanqueId.isBlank() || e.getTanqueId().equals(tanqueId))
                .findFirst().orElse(estados.isEmpty() ? null : estados.get(0));

        List<LecturaNivel> ultimas = new ArrayList<>();
        if (sel != null) {
            var todas = lecturaNivelRepository.findByTanqueIdOrderByTimestampDesc(sel.getTanqueId());
            ultimas = todas.stream().limit(10).toList();
        }
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime ultimaSincronizacion = ultimas.stream()
                .map(LecturaNivel::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        boolean sensorConectado = ultimaSincronizacion != null
                && !ultimaSincronizacion.isAfter(ahora)
                && !ultimaSincronizacion.isBefore(ahora.minusMinutes(5));

        model.addAttribute("tanques", estados);
        model.addAttribute("sel", sel);
        model.addAttribute("ultimas", ultimas);
        model.addAttribute("sensorConectado", sensorConectado);
        model.addAttribute("ultimaSincronizacion", ultimaSincronizacion);
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "dashboard";
    }
}

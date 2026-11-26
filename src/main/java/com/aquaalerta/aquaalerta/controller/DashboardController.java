package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final AlertaRepository alertaRepository;

    public DashboardController(DashboardService dashboardService,
                               AlertaRepository alertaRepository) {
        this.dashboardService = dashboardService;
        this.alertaRepository = alertaRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String panel(@RequestParam(required = false) String tanqueId, Model model) {
        var estados = dashboardService.estadosTanques();
        var sel = estados.stream()
                .filter(e -> tanqueId == null || tanqueId.isBlank() || e.getTanqueId().equals(tanqueId))
                .findFirst().orElse(estados.isEmpty() ? null : estados.get(0));

        List<LecturaNivel> ultimas = List.of();
        int cantidadLecturas = 0;
        boolean sensorConectado = false;
        LocalDateTime ultimaSincronizacion = null;
        if (sel != null) {
            var resumen = dashboardService.resumenLecturas(sel.getTanqueId());
            ultimas = resumen.ultimas();
            cantidadLecturas = resumen.cantidadLecturas();
            sensorConectado = resumen.sensorConectado();
            ultimaSincronizacion = resumen.ultimaSincronizacion();
        }

        model.addAttribute("tanques", estados);
        model.addAttribute("sel", sel);
        model.addAttribute("ultimas", ultimas);
        model.addAttribute("cantidadLecturas", cantidadLecturas);
        model.addAttribute("sensorConectado", sensorConectado);
        model.addAttribute("ultimaSincronizacion", ultimaSincronizacion);
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "dashboard";
    }
}

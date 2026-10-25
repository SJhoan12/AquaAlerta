package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final LecturaNivelRepository lecturaNivelRepository;

    public DashboardController(DashboardService dashboardService,
                               LecturaNivelRepository lecturaNivelRepository) {
        this.dashboardService = dashboardService;
        this.lecturaNivelRepository = lecturaNivelRepository;
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
        model.addAttribute("tanques", estados);
        model.addAttribute("sel", sel);
        model.addAttribute("ultimas", ultimas);
        return "dashboard";
    }
}


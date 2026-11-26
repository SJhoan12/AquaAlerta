package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.dto.TanqueEstadoDto;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTests {

    @Test
    void reportsAllReadingsWhileKeepingOnlyTenForRecentPanel() {
        DashboardService dashboardService = mock(DashboardService.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        when(dashboardService.estadosTanques()).thenReturn(List.of(
                new TanqueEstadoDto("tanque-01", "Tanque 01",
                        96.1, 559.8, 600, 93.3, "NORMAL", "success")));
        when(alertaRepository.findByEstado("ACTIVA")).thenReturn(List.of());

        List<LecturaNivel> lecturas = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            LecturaNivel lectura = new LecturaNivel();
            lectura.setTimestamp(LocalDateTime.now().minusMinutes(i));
            lecturas.add(lectura);
        }
        when(dashboardService.resumenLecturas("tanque-01"))
                .thenReturn(new DashboardService.ResumenLecturas(
                        lecturas.stream().limit(10).toList(), lecturas.size(),
                        lecturas.getFirst().getTimestamp(), true));

        ExtendedModelMap model = new ExtendedModelMap();
        String vista = new DashboardController(dashboardService, alertaRepository)
                .panel(null, model);

        assertEquals("dashboard", vista);
        assertEquals(15, model.get("cantidadLecturas"));
        assertEquals(10, ((List<?>) model.get("ultimas")).size());
    }
}

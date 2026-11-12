package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.dto.TanqueEstadoDto;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTests {

    @Test
    void reportsAllReadingsWhileKeepingOnlyTenForRecentPanel() {
        DashboardService dashboardService = mock(DashboardService.class);
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        when(dashboardService.estadosTanques()).thenReturn(List.of(
                new TanqueEstadoDto("tanque-01", "Tanque 01",
                        96.1, 559.8, 600, 93.3, "NORMAL", "success")));
        when(alertaRepository.findByEstado("ACTIVA")).thenReturn(List.of());

        List<LecturaNivel> lecturas = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            LecturaNivel lectura = new LecturaNivel();
            lectura.setTimestamp(LocalDateTime.now().minusMinutes(i));
            lecturas.add(lectura);
        }
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(lecturas);

        ExtendedModelMap model = new ExtendedModelMap();
        String vista = new DashboardController(
                dashboardService, lecturaRepository, alertaRepository).panel(null, model);

        assertEquals("dashboard", vista);
        assertEquals(15, model.get("cantidadLecturas"));
        assertEquals(10, ((List<?>) model.get("ultimas")).size());
    }
}

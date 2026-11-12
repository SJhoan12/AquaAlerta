package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTests {

    @Test
    void appliesConfiguredMinimumLevelInsteadOfFixedThreshold() {
        Tanque tanque = new Tanque();
        tanque.setId("tanque-01");
        tanque.setNombre("Tanque 01");
        tanque.setAlturaMaxCm(103);
        tanque.setVolumenMax(600);
        tanque.setUmbralMinimo(20);

        LecturaNivel lectura = new LecturaNivel();
        lectura.setNivelCm(19.9);
        lectura.setVolumenLitros(115.9);

        TanqueRepository tanqueRepository = mock(TanqueRepository.class);
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        when(tanqueRepository.findAll()).thenReturn(List.of(tanque));
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(lectura));

        var estado = new DashboardService(tanqueRepository, lecturaRepository)
                .estadosTanques().getFirst();

        assertEquals("BAJO", estado.getEstado());
    }
}

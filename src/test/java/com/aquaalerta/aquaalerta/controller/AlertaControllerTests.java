package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Alerta;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertaControllerTests {

    @Test
    void countsOnlyInformativeAlertsInInformativeSummary() {
        AlertaRepository repository = mock(AlertaRepository.class);
        Alerta informative = alerta("INFORMATIVO");
        Alerta slowLeak = alerta("ANOMALIA_CONSUMO");
        when(repository.findByEstado("ACTIVA")).thenReturn(List.of(informative, slowLeak));
        when(repository.findByEstado("RESUELTA")).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String vista = new AlertaController(repository).bandeja(model);

        assertEquals("alertas", vista);
        assertEquals(2, model.get("numAlertas"));
        assertEquals(1L, model.get("cantidadInformativos"));
    }

    private Alerta alerta(String tipo) {
        Alerta alerta = new Alerta();
        alerta.setTipo(tipo);
        return alerta;
    }
}

package com.aquaalerta.aquaalerta.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcesamientoMuestrasServiceTests {

    private final ProcesamientoMuestrasService service = new ProcesamientoMuestrasService();

    @Test
    void usesMedianAndKeepsAnExtremeOriginalSample() {
        var resultado = service.procesar(List.of(20.0, 21.0, 80.0, 20.0, 19.0), null, 100);

        assertEquals(20.0, resultado.distanciaMedianaCm());
        assertEquals(List.of(20.0, 21.0, 80.0, 20.0, 19.0), resultado.distanciasOriginalesCm());
        assertTrue(resultado.tieneMuestrasAtipicas());
    }

    @Test
    void doesNotMarkConsistentSamplesAsAtypical() {
        var resultado = service.procesar(List.of(20.0, 21.0, 20.5, 19.5, 20.0), null, 100);

        assertEquals(20.0, resultado.distanciaMedianaCm());
        assertFalse(resultado.tieneMuestrasAtipicas());
    }

    @Test
    void supportsTheLegacySingleDistancePayload() {
        var resultado = service.procesar(null, 25.0, 100);

        assertEquals(25.0, resultado.distanciaMedianaCm());
        assertEquals(List.of(25.0), resultado.distanciasOriginalesCm());
        assertFalse(resultado.tieneMuestrasAtipicas());
    }

    @Test
    void rejectsDistancesOutsideThePhysicalTankRange() {
        assertThrows(IllegalArgumentException.class,
                () -> service.procesar(List.of(20.0, 101.0), null, 100));
    }

    @Test
    void rejectsRequestsWithoutAnyDistanceSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> service.procesar(null, null, 100));
    }
}

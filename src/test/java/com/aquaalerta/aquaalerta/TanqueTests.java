package com.aquaalerta.aquaalerta;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TanqueTests {

    @Test
    void defaultsMissingHouseholdCountAndVigilanceForOlderDocuments() {
        Tanque tanque = new Tanque();
        tanque.setNumeroPersonas(null);
        tanque.setModoVigilancia(null);

        assertEquals(4, tanque.getNumeroPersonas());
        assertFalse(tanque.isModoVigilancia());
    }

    @Test
    void calculatesDailyReferenceFromUnifiedHouseholdCount() {
        Tanque tanque = new Tanque();
        tanque.setNumeroPersonas(4);

        assertEquals(320.0 * tanque.obtenerFactorTemporada(),
                tanque.calcularGastoDiarioReferencial(), 0.001);
    }

    @Test
    void validatesUnifiedHouseholdCountFromOneThroughThirty() {
        assertFalse(Tanque.esCantidadPersonasValida(0));
        assertTrue(Tanque.esCantidadPersonasValida(1));
        assertTrue(Tanque.esCantidadPersonasValida(30));
        assertFalse(Tanque.esCantidadPersonasValida(31));
    }

    @Test
    void recognizesInclusiveSummerBoundariesAcrossTheYear() {
        assertEquals(Temporada.VERANO, Tanque.determinarTemporada(LocalDate.of(2026, 12, 21)));
        assertEquals(Temporada.VERANO, Tanque.determinarTemporada(LocalDate.of(2026, 3, 20)));
        assertEquals(Temporada.NORMAL, Tanque.determinarTemporada(LocalDate.of(2026, 3, 21)));
        assertEquals(Temporada.NORMAL, Tanque.determinarTemporada(LocalDate.of(2026, 12, 20)));
    }
}

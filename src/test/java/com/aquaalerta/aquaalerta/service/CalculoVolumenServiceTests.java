package com.aquaalerta.aquaalerta.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculoVolumenServiceTests {

    private final CalculoVolumenService calculoVolumenService = new CalculoVolumenService();

    @Test
    void calculaVolumenEnLitrosParaUnTanqueCilindrico() {
        double volumen = calculoVolumenService.calcularVolumenLitros(100, 100);

        assertEquals(785.3981633974483, volumen, 0.0001);
    }
}

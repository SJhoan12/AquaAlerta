package com.aquaalerta.aquaalerta.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculoVolumenServiceTests {

    private final CalculoVolumenService calculoVolumenService = new CalculoVolumenService();

    @Test
    void estimaLitrosUsandoLaProporcionDelNivelYLaCapacidad() {
        double volumen = calculoVolumenService.calcularVolumenLitros(50, 100, 600);

        assertEquals(300, volumen, 0.0001);
    }

    @Test
    void limitaLosLitrosAlRangoDeCapacidad() {
        assertEquals(0, calculoVolumenService.calcularVolumenLitros(-10, 100, 600));
        assertEquals(600, calculoVolumenService.calcularVolumenLitros(120, 100, 600));
    }

    @Test
    void calculaNivelTomandoComoReferenciaLaDistanciaCuandoElTanqueEstabaLleno() {
        double nivel = calculoVolumenService.calcularNivelCm(45, 10, 100);

        assertEquals(65, nivel, 0.0001);
    }

    @Test
    void limitaElNivelAlRangoDelTanque() {
        assertEquals(0, calculoVolumenService.calcularNivelCm(200, 10, 100));
        assertEquals(100, calculoVolumenService.calcularNivelCm(0, 10, 100));
    }
}

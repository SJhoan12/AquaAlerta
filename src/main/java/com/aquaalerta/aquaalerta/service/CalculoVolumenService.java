package com.aquaalerta.aquaalerta.service;

import org.springframework.stereotype.Service;

@Service
public class CalculoVolumenService {

    public double calcularNivelCm(double distanciaActualCm,
                                  double distanciaCuandoLlenoCm,
                                  double alturaMaximaCm) {
        double alturaAgua = alturaMaximaCm - (distanciaActualCm - distanciaCuandoLlenoCm);
        return Math.max(0.0, Math.min(alturaMaximaCm, alturaAgua));
    }

    public double calcularVolumenLitros(double nivelCm,
                                        double alturaMaximaCm,
                                        double capacidadLitros) {
        if (!Double.isFinite(nivelCm) || !Double.isFinite(alturaMaximaCm)
                || !Double.isFinite(capacidadLitros)
                || alturaMaximaCm <= 0 || capacidadLitros <= 0) {
            throw new IllegalArgumentException("La altura y la capacidad del tanque deben ser mayores que cero.");
        }
        double porcentajeLlenado = Math.max(0.0, Math.min(1.0, nivelCm / alturaMaximaCm));
        return porcentajeLlenado * capacidadLitros;
    }
}

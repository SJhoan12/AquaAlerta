package com.aquaalerta.aquaalerta.service;

import org.springframework.stereotype.Service;

@Service
public class CalculoVolumenService {

    public double calcularVolumenLitros(double alturaCm, double diametroCm) {
        double radioCm = diametroCm / 2.0;
        return Math.PI * radioCm * radioCm * alturaCm / 1000.0;
    }
}

package com.aquaalerta.aquaalerta.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProcesamientoMuestrasService {

    public ResultadoMuestras procesar(List<Double> muestrasDistanciaCm,
                                      Double distanciaCm,
                                      double alturaMaximaCm) {
        if (!Double.isFinite(alturaMaximaCm) || alturaMaximaCm <= 0) {
            throw new IllegalArgumentException("La altura máxima del tanque debe ser mayor que cero.");
        }

        List<Double> muestras = muestrasDistanciaCm == null || muestrasDistanciaCm.isEmpty()
                ? distanciaCm == null ? List.of() : List.of(distanciaCm)
                : new ArrayList<>(muestrasDistanciaCm);
        if (muestras.isEmpty()) {
            throw new IllegalArgumentException("Debe enviarse distanciaCm o al menos una muestra.");
        }
        if (muestras.stream().anyMatch(muestra -> muestra == null
                || !Double.isFinite(muestra)
                || muestra < 0
                || muestra > alturaMaximaCm)) {
            throw new IllegalArgumentException(
                    "Todas las distancias deben ser números entre cero y la altura máxima del tanque.");
        }

        double mediana = mediana(muestras);
        List<Double> desviaciones = muestras.stream()
                .map(muestra -> Math.abs(muestra - mediana))
                .toList();
        double desviacionAbsolutaMediana = mediana(desviaciones);
        double limiteAtipico = Math.max(2.0, 3.0 * 1.4826 * desviacionAbsolutaMediana);
        boolean tieneMuestrasAtipicas = desviaciones.stream()
                .anyMatch(desviacion -> desviacion > limiteAtipico);

        return new ResultadoMuestras(mediana, List.copyOf(muestras), tieneMuestrasAtipicas);
    }

    private double mediana(List<Double> valores) {
        List<Double> ordenados = valores.stream().sorted(Comparator.naturalOrder()).toList();
        int medio = ordenados.size() / 2;
        if (ordenados.size() % 2 == 1) {
            return ordenados.get(medio);
        }
        return (ordenados.get(medio - 1) + ordenados.get(medio)) / 2.0;
    }

    public record ResultadoMuestras(
            double distanciaMedianaCm,
            List<Double> distanciasOriginalesCm,
            boolean tieneMuestrasAtipicas) {
    }
}

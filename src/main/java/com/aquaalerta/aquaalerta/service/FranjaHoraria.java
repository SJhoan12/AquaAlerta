package com.aquaalerta.aquaalerta.service;

import java.time.LocalDateTime;

/**
 * Franjas horarias según sección 12.3 del informe:
 * MADRUGADA 00:00-06:00, MANANA 06:00-12:00,
 * TARDE 12:00-18:00, NOCHE 18:00-24:00.
 */
public enum FranjaHoraria {
    MADRUGADA("MADRUGADA", "madrugada", 0, 6),
    MANANA("MANANA", "mañana", 6, 12),
    TARDE("TARDE", "tarde", 12, 18),
    NOCHE("NOCHE", "noche", 18, 24);

    private final String codigo;
    private final String etiqueta;
    private final int horaInicio;
    private final int horaFin;

    FranjaHoraria(String codigo, String etiqueta, int horaInicio, int horaFin) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }

    public static FranjaHoraria de(LocalDateTime fecha) {
        int h = fecha.getHour();
        for (FranjaHoraria f : values()) {
            if (h >= f.horaInicio && h < f.horaFin) return f;
        }
        return NOCHE;
    }
}

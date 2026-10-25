package com.aquaalerta.aquaalerta.dto;

public class TanqueEstadoDto {
    private String tanqueId;
    private String nombre;
    private double nivelCm;
    private double volumenLitros;
    private double volumenMax;
    private double porcentaje;
    private String estado;
    private String estadoCss;

    public TanqueEstadoDto() {
    }

    public TanqueEstadoDto(String tanqueId, String nombre, double nivelCm, double volumenLitros,
                           double volumenMax, double porcentaje, String estado, String estadoCss) {
        this.tanqueId = tanqueId;
        this.nombre = nombre;
        this.nivelCm = nivelCm;
        this.volumenLitros = volumenLitros;
        this.volumenMax = volumenMax;
        this.porcentaje = porcentaje;
        this.estado = estado;
        this.estadoCss = estadoCss;
    }

    public String getTanqueId() {
        return tanqueId;
    }

    public void setTanqueId(String tanqueId) {
        this.tanqueId = tanqueId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public double getNivelCm() {
        return nivelCm;
    }

    public void setNivelCm(double nivelCm) {
        this.nivelCm = nivelCm;
    }

    public double getVolumenLitros() {
        return volumenLitros;
    }

    public void setVolumenLitros(double volumenLitros) {
        this.volumenLitros = volumenLitros;
    }

    public double getVolumenMax() {
        return volumenMax;
    }

    public void setVolumenMax(double volumenMax) {
        this.volumenMax = volumenMax;
    }

    public double getPorcentaje() {
        return porcentaje;
    }

    public void setPorcentaje(double porcentaje) {
        this.porcentaje = porcentaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getEstadoCss() {
        return estadoCss;
    }

    public void setEstadoCss(String estadoCss) {
        this.estadoCss = estadoCss;
    }
}



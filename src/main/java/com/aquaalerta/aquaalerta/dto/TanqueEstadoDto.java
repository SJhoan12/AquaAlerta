package com.aquaalerta.aquaalerta.dto;

import com.aquaalerta.aquaalerta.Temporada;

public class TanqueEstadoDto {
    private String tanqueId;
    private String nombre;
    private double nivelCm;
    private double volumenLitros;
    private double volumenMax;
    private double porcentaje;
    private String estado;
    private String estadoCss;
    private int numeroPersonas;
    private double gastoDiarioReferencial;
    private Temporada temporada;
    private boolean modoVigilancia;

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

    public TanqueEstadoDto(String tanqueId, String nombre, double nivelCm, double volumenLitros,
                           double volumenMax, double porcentaje, String estado, String estadoCss,
                           int numeroPersonas, double gastoDiarioReferencial,
                           Temporada temporada, boolean modoVigilancia) {
        this(tanqueId, nombre, nivelCm, volumenLitros, volumenMax, porcentaje, estado, estadoCss);
        this.numeroPersonas = numeroPersonas;
        this.gastoDiarioReferencial = gastoDiarioReferencial;
        this.temporada = temporada;
        this.modoVigilancia = modoVigilancia;
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

    public int getNumeroPersonas() {
        return numeroPersonas;
    }

    public double getGastoDiarioReferencial() {
        return gastoDiarioReferencial;
    }

    public Temporada getTemporada() {
        return temporada;
    }

    public boolean isModoVigilancia() {
        return modoVigilancia;
    }
}



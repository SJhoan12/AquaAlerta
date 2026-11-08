package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "tanques")
public class Tanque {

    @Id
    private String id;

    private String nombre;
    private double alturaMaxCm;
    private double volumenMax;
    private Double distanciaLlenoCm;
    private LocalDateTime referenciaLlenoActualizada;
    private double umbralMinimo = 5;
    private double margenInformativoPct = 20;
    private double umbralAnomaliaPct = 50;
    private String usuarioId;

    public Tanque() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public double getAlturaMaxCm() {
        return alturaMaxCm;
    }

    public void setAlturaMaxCm(double alturaMaxCm) {
        this.alturaMaxCm = alturaMaxCm;
    }

    public double getVolumenMax() {
        return volumenMax;
    }

    public void setVolumenMax(double volumenMax) {
        this.volumenMax = volumenMax;
    }

    public Double getDistanciaLlenoCm() {
        return distanciaLlenoCm;
    }

    public void setDistanciaLlenoCm(Double distanciaLlenoCm) {
        this.distanciaLlenoCm = distanciaLlenoCm;
    }

    public LocalDateTime getReferenciaLlenoActualizada() {
        return referenciaLlenoActualizada;
    }

    public void setReferenciaLlenoActualizada(LocalDateTime referenciaLlenoActualizada) {
        this.referenciaLlenoActualizada = referenciaLlenoActualizada;
    }

    public double getUmbralMinimo() {
        return umbralMinimo;
    }

    public void setUmbralMinimo(double umbralMinimo) {
        this.umbralMinimo = umbralMinimo;
    }

    public double getMargenInformativoPct() {
        return margenInformativoPct;
    }

    public void setMargenInformativoPct(double margenInformativoPct) {
        this.margenInformativoPct = margenInformativoPct;
    }

    public double getUmbralAnomaliaPct() {
        return umbralAnomaliaPct;
    }

    public void setUmbralAnomaliaPct(double umbralAnomaliaPct) {
        this.umbralAnomaliaPct = umbralAnomaliaPct;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }
}

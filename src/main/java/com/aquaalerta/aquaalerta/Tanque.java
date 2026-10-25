package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tanques")
public class Tanque {

    @Id
    private String id;

    private String nombre;
    private double alturaMaxCm;
    private double diametroCm;
    private double volumenMax;
    private double umbralMinimo;
    private double margenInformativoPct;
    private double umbralAnomaliaPct;
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

    public double getDiametroCm() {
        return diametroCm;
    }

    public void setDiametroCm(double diametroCm) {
        this.diametroCm = diametroCm;
    }

    public double getVolumenMax() {
        return volumenMax;
    }

    public void setVolumenMax(double volumenMax) {
        this.volumenMax = volumenMax;
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

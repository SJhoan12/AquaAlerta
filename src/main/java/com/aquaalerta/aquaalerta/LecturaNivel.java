package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "lecturasNivel")
public class LecturaNivel {

    @Id
    private String id;
    private String tanqueId;
    private String sensorId;
    private double distanciaCm;
    private List<Double> distanciasOriginalesCm = new ArrayList<>();
    private boolean tieneMuestrasAtipicas;
    private double nivelCm;
    private double volumenLitros;
    private LocalDateTime timestamp;
    private boolean esAnomalia;

    public LecturaNivel() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTanqueId() {
        return tanqueId;
    }

    public void setTanqueId(String tanqueId) {
        this.tanqueId = tanqueId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public double getDistanciaCm() {
        return distanciaCm;
    }

    public void setDistanciaCm(double distanciaCm) {
        this.distanciaCm = distanciaCm;
    }

    public List<Double> getDistanciasOriginalesCm() {
        return distanciasOriginalesCm;
    }

    public void setDistanciasOriginalesCm(List<Double> distanciasOriginalesCm) {
        this.distanciasOriginalesCm = distanciasOriginalesCm == null
                ? new ArrayList<>()
                : new ArrayList<>(distanciasOriginalesCm);
    }

    public boolean isTieneMuestrasAtipicas() {
        return tieneMuestrasAtipicas;
    }

    public void setTieneMuestrasAtipicas(boolean tieneMuestrasAtipicas) {
        this.tieneMuestrasAtipicas = tieneMuestrasAtipicas;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isEsAnomalia() {
        return esAnomalia;
    }

    public void setEsAnomalia(boolean esAnomalia) {
        this.esAnomalia = esAnomalia;
    }
}

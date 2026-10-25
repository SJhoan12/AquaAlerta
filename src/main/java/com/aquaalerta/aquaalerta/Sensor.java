package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sensores")
public class Sensor {

    @Id
    private String id;
    private String tipo;
    private String estado;
    private String tanqueId;

    public Sensor() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTanqueId() {
        return tanqueId;
    }

    public void setTanqueId(String tanqueId) {
        this.tanqueId = tanqueId;
    }
}

package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "alertas")
public class Alerta {

    @Id
    private String id;
    private String tanqueId;
    private String tipo;
    private String descripcion;
    private double desviacionPorcentaje;
    private double umbralAplicadoPct;
    private LocalDateTime timestamp;
    private String estado;
    private boolean resuelta;

    public Alerta() {
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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public double getDesviacionPorcentaje() {
        return desviacionPorcentaje;
    }

    public void setDesviacionPorcentaje(double desviacionPorcentaje) {
        this.desviacionPorcentaje = desviacionPorcentaje;
    }

    public double getUmbralAplicadoPct() {
        return umbralAplicadoPct;
    }

    public void setUmbralAplicadoPct(double umbralAplicadoPct) {
        this.umbralAplicadoPct = umbralAplicadoPct;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isResuelta() {
        return resuelta;
    }

    public void setResuelta(boolean resuelta) {
        this.resuelta = resuelta;
    }
}

package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "patronesConsumo")
public class PatronConsumo {

    @Id
    private String id;
    private String tanqueId;
    private String diaSemana;
    private String franja;
    private double consumoReferenciaLitros;
    private int semanasConsideradas;
    private LocalDateTime actualizado;

    public PatronConsumo() {
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

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public String getFranja() {
        return franja;
    }

    public void setFranja(String franja) {
        this.franja = franja;
    }

    public double getConsumoReferenciaLitros() {
        return consumoReferenciaLitros;
    }

    public void setConsumoReferenciaLitros(double consumoReferenciaLitros) {
        this.consumoReferenciaLitros = consumoReferenciaLitros;
    }

    public int getSemanasConsideradas() {
        return semanasConsideradas;
    }

    public void setSemanasConsideradas(int semanasConsideradas) {
        this.semanasConsideradas = semanasConsideradas;
    }

    public LocalDateTime getActualizado() {
        return actualizado;
    }

    public void setActualizado(LocalDateTime actualizado) {
        this.actualizado = actualizado;
    }
}

package com.aquaalerta.aquaalerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Configuración de un tanque y cálculo de su referencia de consumo doméstico.
 * La referencia de consumo se basa en MVCS (2024) y los patrones horarios en SUNASS (2025).
 */
@Document(collection = "tanques")
public class Tanque {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final MonthDay INICIO_VERANO = MonthDay.of(Month.DECEMBER, 21);
    private static final MonthDay FIN_VERANO = MonthDay.of(Month.MARCH, 20);
    private static final int NUMERO_PERSONAS_POR_DEFECTO = 4;
    private static final int NUMERO_PERSONAS_MINIMO = 1;
    private static final int NUMERO_PERSONAS_MAXIMO = 30;
    private static final double CONSUMO_DIARIO_POR_PERSONA_LITROS = 80.0;
    private static final double FACTOR_TEMPORADA_VERANO = 1.20;
    private static final double FACTOR_TEMPORADA_NORMAL = 1.0;

    @Id
    private String id;

    private String nombre;
    private double alturaMaxCm;
    private double volumenMax;
    private Double distanciaLlenoCm;
    private LocalDateTime referenciaLlenoActualizada;
    private Integer numeroPersonas = NUMERO_PERSONAS_POR_DEFECTO;
    private Boolean modoVigilancia = false;
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

    public Integer getNumeroPersonas() {
        return Objects.requireNonNullElse(numeroPersonas, NUMERO_PERSONAS_POR_DEFECTO);
    }

    public void setNumeroPersonas(Integer numeroPersonas) {
        this.numeroPersonas = numeroPersonas;
    }

    public boolean isModoVigilancia() {
        return Boolean.TRUE.equals(modoVigilancia);
    }

    public void setModoVigilancia(Boolean modoVigilancia) {
        this.modoVigilancia = modoVigilancia;
    }

    /**
     * Determina la temporada con el calendario local de Lima; verano cruza el cambio de año.
     * El calendario estacional sigue la referencia de MVCS (2024) y SUNASS (2025).
     */
    public Temporada obtenerTemporadaActual() {
        return determinarTemporada(LocalDate.now(ZONA_LIMA));
    }

    /**
     * Clasifica verano entre el 21 de diciembre y el 20 de marzo, ambos inclusive.
     * La ventana estacional está basada en las referencias de MVCS (2024) y SUNASS (2025).
     */
    static Temporada determinarTemporada(LocalDate fecha) {
        MonthDay fechaLocal = MonthDay.from(Objects.requireNonNull(fecha, "fecha"));
        return !fechaLocal.isBefore(INICIO_VERANO) || !fechaLocal.isAfter(FIN_VERANO)
                ? Temporada.VERANO
                : Temporada.NORMAL;
    }

    /**
     * Aplica 1,20 en verano y 1,0 el resto del año, según MVCS (2024) y SUNASS (2025).
     */
    public double obtenerFactorTemporada() {
        return obtenerTemporadaActual() == Temporada.VERANO
                ? FACTOR_TEMPORADA_VERANO
                : FACTOR_TEMPORADA_NORMAL;
    }

    /**
     * Calcula personas × 80 litros diarios × factor estacional, según MVCS (2024).
     */
    public double calcularGastoDiarioReferencial() {
        return getNumeroPersonas() * CONSUMO_DIARIO_POR_PERSONA_LITROS
                * obtenerFactorTemporada();
    }

    public static boolean esCantidadPersonasValida(Integer cantidad) {
        return cantidad != null
                && cantidad >= NUMERO_PERSONAS_MINIMO
                && cantidad <= NUMERO_PERSONAS_MAXIMO;
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

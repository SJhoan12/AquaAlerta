package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Alerta;
import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.PatronConsumo;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.PatronConsumoRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DeteccionAnomaliasService {

    private static final int VENTANA_MINUTOS = 3;
    private static final int MAX_LECTURAS_RECIENTES = 6;
    private static final int MAX_SEPARACION_MINUTOS = 5;
    private static final int EVALUACIONES_CONSECUTIVAS_REQUERIDAS = 2;
    private static final double CAIDA_MINIMA_CM = 0.1;
    private static final double FRACCION_MINIMA_ALTURA = 0.005;
    private static final double PESO_ACTUALIZACION_PATRON = 0.10;

    private final LecturaNivelRepository lecturaRepo;
    private final PatronConsumoRepository patronRepo;
    private final AlertaRepository alertaRepo;

    public DeteccionAnomaliasService(LecturaNivelRepository lecturaRepo,
                                     PatronConsumoRepository patronRepo,
                                     AlertaRepository alertaRepo) {
        this.lecturaRepo = lecturaRepo;
        this.patronRepo = patronRepo;
        this.alertaRepo = alertaRepo;
    }

    public void evaluar(Tanque tanque, LecturaNivel guardada) {
        if (guardada.isTieneMuestrasAtipicas()) {
            return;
        }
        LocalDateTime ts = guardada.getTimestamp();
        if (ts == null) {
            ts = LocalDateTime.now();
        }
        String dia = diaSemana(ts);
        FranjaHoraria franja = FranjaHoraria.de(ts);
        String rango = rangoFranja(franja);
        PatronConsumo patron = patronRepo
                .findByTanqueIdAndDiaSemanaAndFranja(tanque.getId(), dia, franja.getCodigo())
                .orElseGet(() -> nuevoPatron(tanque.getId(), dia, franja.getCodigo()));
        LocalDateTime ultimaEvaluacion = patron.getUltimaEvaluacionTasa();
        if (ultimaEvaluacion != null && !ts.isAfter(ultimaEvaluacion)) {
            return;
        }
        if (guardada.getTimestamp() == null) {
            guardada.setTimestamp(ts);
        }

        Optional<Double> tasaActual = calcularTasaConsumo(
                lecturaRepo.findByTanqueIdOrderByTimestampDesc(tanque.getId()),
                guardada, ts, tanque.getAlturaMaxCm(), tanque.getReferenciaLlenoActualizada());
        patron.setUltimaEvaluacionTasa(ts);
        patron.setActualizado(ts);

        if (tasaActual.isEmpty()) {
            patron.setEvaluacionesAnomalasConsecutivas(0);
            patronRepo.save(patron);
            return;
        }

        double tasa = tasaActual.get();
        double ref = patron.getTasaReferenciaPctNivelPorMinuto();
        if (patron.getMuestrasTasaReferenciaNivel() == 0 || ref <= 0) {
            registrarReferenciaInicial(patron, tasa);
            patronRepo.save(patron);
            return;
        }

        double desv = ((tasa - ref) / ref) * 100.0;
        double margen = tanque.getMargenInformativoPct();
        double umbral = tanque.getUmbralAnomaliaPct();
        String desc = "El nivel bajó " + red(desv) + "% más rápido de lo habitual del "
                + dia.toLowerCase(Locale.ROOT) + " en la franja " + rango
                + " (referencia: " + red(ref) + "% de la altura por minuto)";

        if (desv > umbral) {
            boolean evaluacionConsecutiva = ultimaEvaluacion != null
                    && java.time.Duration.between(ultimaEvaluacion, ts).getSeconds()
                    <= MAX_SEPARACION_MINUTOS * 60L;
            patron.setEvaluacionesAnomalasConsecutivas(evaluacionConsecutiva
                    ? Math.min(EVALUACIONES_CONSECUTIVAS_REQUERIDAS,
                            patron.getEvaluacionesAnomalasConsecutivas() + 1)
                    : 1);
            if (patron.getEvaluacionesAnomalasConsecutivas()
                    >= EVALUACIONES_CONSECUTIVAS_REQUERIDAS
                    && !existeAlertaHoy(tanque.getId(), ts.toLocalDate(), rango, "ANOMALIA_CONSUMO")) {
                alertaRepo.save(nueva(tanque.getId(), "ANOMALIA_CONSUMO", desc, desv, umbral, ts));
                guardada.setEsAnomalia(true);
                lecturaRepo.save(guardada);
            }
        } else {
            patron.setEvaluacionesAnomalasConsecutivas(0);
            if (desv > margen
                    && !existeAlertaHoy(tanque.getId(), ts.toLocalDate(), rango, "INFORMATIVO")) {
                alertaRepo.save(nueva(tanque.getId(), "INFORMATIVO", desc, desv, margen, ts));
            } else if (desv <= margen) {
                actualizarPatron(patron, tasa);
            }
        }
        patronRepo.save(patron);
    }

    private PatronConsumo nuevoPatron(String tanqueId, String dia, String franja) {
        PatronConsumo patron = new PatronConsumo();
        patron.setTanqueId(tanqueId);
        patron.setDiaSemana(dia);
        patron.setFranja(franja);
        return patron;
    }

    private void registrarReferenciaInicial(PatronConsumo patron, double tasa) {
        patron.setTasaReferenciaPctNivelPorMinuto(tasa);
        patron.setMuestrasTasaReferenciaNivel(1);
        patron.setEvaluacionesAnomalasConsecutivas(0);
    }

    private void actualizarPatron(PatronConsumo patron, double tasa) {
        double referencia = patron.getTasaReferenciaPctNivelPorMinuto();
        double actualizacion = referencia
                + PESO_ACTUALIZACION_PATRON * (tasa - referencia);
        patron.setTasaReferenciaPctNivelPorMinuto(actualizacion);
        patron.setMuestrasTasaReferenciaNivel(patron.getMuestrasTasaReferenciaNivel() + 1);
    }

    private Optional<Double> calcularTasaConsumo(List<LecturaNivel> todas,
                                                  LecturaNivel actual,
                                                  LocalDateTime ts,
                                                  double alturaMaximaCm,
                                                  LocalDateTime referenciaLlenoActualizada) {
        if (!Double.isFinite(alturaMaximaCm) || alturaMaximaCm <= 0) {
            return Optional.empty();
        }
        LocalDateTime inicioVentana = ts.minusMinutes(VENTANA_MINUTOS);
        List<LecturaNivel> recientes = new ArrayList<>(todas.stream()
                .filter(lectura -> lectura.getTimestamp() != null)
                .filter(lectura -> referenciaLlenoActualizada == null
                        || lectura.getTimestamp().isAfter(referenciaLlenoActualizada))
                .filter(lectura -> !lectura.isTieneMuestrasAtipicas())
                .filter(lectura -> !lectura.getTimestamp().isAfter(ts))
                .filter(lectura -> !lectura.getTimestamp().isBefore(inicioVentana))
                .toList());
        if (recientes.stream().noneMatch(lectura -> actual.getId() == null
                ? lectura == actual
                : actual.getId().equals(lectura.getId()))) {
            recientes.add(actual);
        }
        recientes.sort(Comparator.comparing(LecturaNivel::getTimestamp));
        if (recientes.size() > MAX_LECTURAS_RECIENTES) {
            recientes = new ArrayList<>(recientes.subList(
                    recientes.size() - MAX_LECTURAS_RECIENTES, recientes.size()));
        }
        if (recientes.size() < 2) {
            return Optional.empty();
        }

        int inicioSegmento = recientes.size() - 1;
        for (int i = recientes.size() - 1; i > 0; i--) {
            long segundos = java.time.Duration.between(
                    recientes.get(i - 1).getTimestamp(), recientes.get(i).getTimestamp()).getSeconds();
            if (segundos > MAX_SEPARACION_MINUTOS * 60L) {
                break;
            }
            inicioSegmento = i - 1;
        }
        List<LecturaNivel> segmento = recientes.subList(inicioSegmento, recientes.size());
        if (segmento.size() < 2) {
            return Optional.empty();
        }

        LecturaNivel primera = segmento.get(0);
        LecturaNivel ultima = segmento.get(segmento.size() - 1);
        double minutos = java.time.Duration.between(
                primera.getTimestamp(), ultima.getTimestamp()).toMillis() / 60_000.0;
        if (minutos <= 0) {
            return Optional.empty();
        }
        double nivelBajadoCm = primera.getNivelCm() - ultima.getNivelCm();
        double caidaMinimaCm = Math.max(CAIDA_MINIMA_CM, alturaMaximaCm * FRACCION_MINIMA_ALTURA);
        if (nivelBajadoCm < caidaMinimaCm) {
            return Optional.empty();
        }
        return Optional.of(nivelBajadoCm / alturaMaximaCm * 100.0 / minutos);
    }

    private boolean existeAlertaHoy(String tanqueId, LocalDate fecha, String rango, String tipo) {
        return alertaRepo.findByTanqueId(tanqueId).stream().anyMatch(a ->
                a.getTimestamp() != null && a.getTimestamp().toLocalDate().equals(fecha)
                        && tipo.equals(a.getTipo())
                        && a.getDescripcion() != null && a.getDescripcion().contains(rango));
    }

    private Alerta nueva(String tanqueId, String tipo, String desc, double desv, double umbral, LocalDateTime ts) {
        Alerta a = new Alerta();
        a.setTanqueId(tanqueId);
        a.setTipo(tipo);
        a.setDescripcion(desc);
        a.setDesviacionPorcentaje(Math.round(desv * 10.0) / 10.0);
        a.setUmbralAplicadoPct(umbral);
        a.setTimestamp(ts);
        a.setEstado("ACTIVA");
        a.setResuelta(false);
        return a;
    }

    private String diaSemana(LocalDateTime ts) {
        DayOfWeek d = ts.getDayOfWeek();
        if (d == DayOfWeek.MONDAY) return "LUNES";
        if (d == DayOfWeek.TUESDAY) return "MARTES";
        if (d == DayOfWeek.WEDNESDAY) return "MIERCOLES";
        if (d == DayOfWeek.THURSDAY) return "JUEVES";
        if (d == DayOfWeek.FRIDAY) return "VIERNES";
        if (d == DayOfWeek.SATURDAY) return "SABADO";
        return "DOMINGO";
    }

    private String rangoFranja(FranjaHoraria f) {
        if (f == FranjaHoraria.MADRUGADA) return "00:00-06:00";
        if (f == FranjaHoraria.MANANA) return "06:00-12:00";
        if (f == FranjaHoraria.TARDE) return "12:00-18:00";
        return "18:00-24:00";
    }

    private String red(double valor) {
        return String.format(Locale.ROOT, "%.3f", valor);
    }
}

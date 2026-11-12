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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DeteccionAnomaliasService {

    private static final int VENTANA_MINUTOS = 4;
    private static final int MAX_LECTURAS_RECIENTES = 10;
    private static final int MAX_SEPARACION_MINUTOS = 5;
    private static final int EVALUACIONES_CONSECUTIVAS_REQUERIDAS = 2;
    private static final double CAIDA_MINIMA_CM = 0.1;
    private static final double FRACCION_MINIMA_ALTURA = 0.001;
    private static final double DURACION_FISICA_MINIMA_MINUTOS = 3.0;
    private static final double VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO = 8.0;
    private static final double MULTIPLICADOR_INTRA_DIA = 2.5;
    private static final double PESO_ACTUALIZACION_PATRON = 0.10;
    private static final double FRACCION_MINIMA_CONSUMO_DIARIO = 0.005;
    private static final double FRACCION_MINIMA_MOVIMIENTO_DIARIO = 0.0005;
    private static final int VENTANA_RITMO_DIARIO_HORAS = 4;
    private static final int MAX_SEPARACION_RITMO_DIARIO_MINUTOS = 5;
    private static final double MOVIMIENTO_MINIMO_RITMO_DIARIO_LITROS = 0.1;

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

        LocalDateTime ts = guardada.getTimestamp() == null
                ? LocalDateTime.now()
                : guardada.getTimestamp();
        if (guardada.getTimestamp() == null) {
            guardada.setTimestamp(ts);
        }

        List<LecturaNivel> lecturas = lecturaRepo
                .findByTanqueIdOrderByTimestampDesc(tanque.getId());
        // Si se registró nuevamente el tanque lleno, esa referencia inicia un ciclo de aprendizaje nuevo.
        LocalDateTime primeraLectura = primeraLectura(
                lecturas, ts, tanque.getReferenciaLlenoActualizada());
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
        patron.setUltimaEvaluacionTasa(ts);
        patron.setActualizado(ts);

        Optional<TasaConsumo> tasaActual = calcularTasaConsumo(
                lecturas, guardada, ts, tanque.getAlturaMaxCm(),
                tanque.getReferenciaLlenoActualizada(), tanque.getVolumenMax());
        if (tasaActual.isEmpty()) {
            patron.setEvaluacionesAnomalasConsecutivas(0);
            patronRepo.save(patron);
            return;
        }

        TasaConsumo tasa = tasaActual.get();
        Optional<TasaConsumo> ritmoDiario = calcularRitmoDiario(
                lecturas, ts, tanque.getReferenciaLlenoActualizada(), tanque.getVolumenMax());
        Fase fase = determinarFase(primeraLectura, ts);
        CandidatoAnomalia candidato = evaluarFase(
                fase, tanque, patron, tasa, ritmoDiario, dia, rango);

        if (candidato != null) {
            confirmarAnomalia(tanque, guardada, patron, candidato,
                    ultimaEvaluacion, ts, rango);
        } else {
            patron.setEvaluacionesAnomalasConsecutivas(0);
            if (fase == Fase.HISTORICO) {
                evaluarInformativo(tanque, patron, tasa, ts, dia, rango);
            }
            if (tasa.porcentajeAlturaPorMinuto() > 0) {
                actualizarPatron(patron, tasa.porcentajeAlturaPorMinuto());
            }
        }
        patronRepo.save(patron);
    }

    private CandidatoAnomalia evaluarFase(Fase fase,
                                           Tanque tanque,
                                           PatronConsumo patron,
                                           TasaConsumo tasa,
                                           Optional<TasaConsumo> ritmoDiario,
                                           String dia,
                                           String rango) {
        boolean superaReglaFisica = superaReglaFisica(tasa);
        if (fase == Fase.ARRANQUE_EN_FRIO) {
            // Fase 0: durante las primeras cuatro horas solo se aplica el límite físico.
            return superaReglaFisica
                    ? candidatoFisico(tasa, "Fase 0 (arranque en frío)", rango)
                    : null;
        }

        if (fase == Fase.INTRA_DIA) {
            // Fase 1: se combina el límite físico con el ritmo acumulado del día actual.
            if (superaReglaFisica) {
                return candidatoFisico(tasa, "Fase 1 (comparación intra-día)", rango);
            }
            if (ritmoDiario.isEmpty()
                    || ritmoDiario.get().litrosPorMinuto() <= 0
                    || tasa.litrosPorMinuto()
                    < ritmoDiario.get().litrosPorMinuto() * MULTIPLICADOR_INTRA_DIA) {
                return null;
            }
            double multiplicador = tasa.litrosPorMinuto()
                    / ritmoDiario.get().litrosPorMinuto();
            return new CandidatoAnomalia(
                    "Fase 1 (comparación intra-día): el descenso actual es "
                            + red(multiplicador) + " veces el ritmo promedio de hoy ("
                            + redLitros(tasa.litrosPorMinuto()) + " L/min frente a "
                            + redLitros(ritmoDiario.get().litrosPorMinuto())
                            + " L/min) en la franja " + rango + ".",
                    multiplicador * 100.0,
                    MULTIPLICADOR_INTRA_DIA * 100.0);
        }

        // Fase 2: solo el patrón histórico de día y franja, con el umbral configurable.
        double referenciaHistorica = patron.getTasaReferenciaPctNivelPorMinuto();
        if (referenciaHistorica <= 0) {
            return null;
        }

        double desviacion = ((tasa.porcentajeAlturaPorMinuto() - referenciaHistorica)
                / referenciaHistorica) * 100.0;
        double umbral = tanque.getUmbralAnomaliaPct();
        if (desviacion <= umbral) {
            return null;
        }
        String descripcion = "Fase 2: el nivel bajó " + redPorcentaje(desviacion)
                + "% más rápido que el patrón histórico del "
                + dia.toLowerCase(Locale.ROOT) + " en la franja " + rango
                + " (referencia: " + redTasa(referenciaHistorica)
                + "% de la altura por minuto).";
        return new CandidatoAnomalia(descripcion, desviacion, umbral);
    }

    private void evaluarInformativo(Tanque tanque,
                                    PatronConsumo patron,
                                    TasaConsumo tasa,
                                    LocalDateTime ts,
                                    String dia,
                                    String rango) {
        double referenciaHistorica = patron.getTasaReferenciaPctNivelPorMinuto();
        if (referenciaHistorica <= 0) {
            return;
        }

        double desviacion = ((tasa.porcentajeAlturaPorMinuto() - referenciaHistorica)
                / referenciaHistorica) * 100.0;
        double margen = tanque.getMargenInformativoPct();
        if (desviacion > margen
                && !existeAlertaHoy(tanque.getId(), ts.toLocalDate(), rango, "INFORMATIVO")) {
            String descripcion = "El nivel bajó " + redPorcentaje(desviacion)
                    + "% más rápido que el patrón histórico del "
                    + dia.toLowerCase(Locale.ROOT) + " en la franja " + rango
                    + " (referencia histórica: " + redTasa(referenciaHistorica)
                    + "% de la altura por minuto).";
            alertaRepo.save(nueva(tanque.getId(), "INFORMATIVO", descripcion,
                    desviacion, margen, ts));
        }
    }

    private void confirmarAnomalia(Tanque tanque,
                                   LecturaNivel guardada,
                                   PatronConsumo patron,
                                   CandidatoAnomalia candidato,
                                   LocalDateTime ultimaEvaluacion,
                                   LocalDateTime ts,
                                   String rango) {
        boolean consecutiva = ultimaEvaluacion != null
                && Duration.between(ultimaEvaluacion, ts).getSeconds()
                <= MAX_SEPARACION_MINUTOS * 60L;
        patron.setEvaluacionesAnomalasConsecutivas(consecutiva
                ? Math.min(EVALUACIONES_CONSECUTIVAS_REQUERIDAS,
                        patron.getEvaluacionesAnomalasConsecutivas() + 1)
                : 1);
        if (patron.getEvaluacionesAnomalasConsecutivas()
                < EVALUACIONES_CONSECUTIVAS_REQUERIDAS) {
            return;
        }

        guardada.setEsAnomalia(true);
        lecturaRepo.save(guardada);
        if (!existeAlertaHoy(tanque.getId(), ts.toLocalDate(), rango, "ANOMALIA_CONSUMO")) {
            alertaRepo.save(nueva(tanque.getId(), "ANOMALIA_CONSUMO",
                    candidato.descripcion(), candidato.desviacionPorcentaje(),
                    candidato.umbralAplicado(), ts));
        }
    }

    private boolean superaReglaFisica(TasaConsumo tasa) {
        return tasa.caidaContinua()
                && tasa.duracionMinutos() >= DURACION_FISICA_MINIMA_MINUTOS
                && tasa.litrosPorMinuto() > VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO;
    }

    private CandidatoAnomalia candidatoFisico(TasaConsumo tasa, String fase, String rango) {
        double porcentajeDelLimite = tasa.litrosPorMinuto()
                / VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO * 100.0;
        return new CandidatoAnomalia(
                fase + ": posible fuga; caída sostenida de "
                        + redLitros(tasa.litrosPorMinuto()) + " L/min durante "
                        + red(tasa.duracionMinutos()) + " minutos (límite: "
                        + redLitros(VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO)
                        + " L/min) en la franja " + rango + ".",
                porcentajeDelLimite,
                100.0);
    }

    private Fase determinarFase(LocalDateTime primeraLectura, LocalDateTime ts) {
        long minutosDesdeInicio = Math.max(0, Duration.between(primeraLectura, ts).toMinutes());
        if (minutosDesdeInicio < Duration.ofHours(4).toMinutes()) {
            return Fase.ARRANQUE_EN_FRIO;
        }
        if (minutosDesdeInicio < Duration.ofHours(24).toMinutes()) {
            return Fase.INTRA_DIA;
        }
        return Fase.HISTORICO;
    }

    private LocalDateTime primeraLectura(List<LecturaNivel> lecturas,
                                         LocalDateTime ts,
                                         LocalDateTime inicioMonitoreo) {
        return lecturas.stream()
                .filter(lectura -> !lectura.isTieneMuestrasAtipicas())
                .filter(lectura -> Double.isFinite(lectura.getNivelCm())
                        && Double.isFinite(lectura.getVolumenLitros())
                        && lectura.getNivelCm() >= 0
                        && lectura.getVolumenLitros() >= 0)
                .map(LecturaNivel::getTimestamp)
                .filter(timestamp -> timestamp != null && !timestamp.isAfter(ts))
                .filter(timestamp -> inicioMonitoreo == null || timestamp.isAfter(inicioMonitoreo))
                .min(Comparator.naturalOrder())
                .orElse(ts);
    }

    private Optional<TasaConsumo> calcularRitmoDiario(List<LecturaNivel> todas,
                                                       LocalDateTime ts,
                                                       LocalDateTime referenciaLlenoActualizada,
                                                       double capacidadLitros) {
        // Fase 1 usa solo las últimas horas y excluye periodos ociosos y recargas.
        LocalDateTime limiteDesde = ts.minusHours(VENTANA_RITMO_DIARIO_HORAS);
        LocalDateTime limiteHasta = ts.minusMinutes(VENTANA_MINUTOS);
        if (!Double.isFinite(capacidadLitros) || capacidadLitros <= 0) {
            return Optional.empty();
        }
        List<LecturaNivel> lecturasDia = todas.stream()
                .filter(lectura -> lectura.getTimestamp() != null)
                .filter(lectura -> !lectura.getTimestamp().toLocalDate().isBefore(ts.toLocalDate()))
                .filter(lectura -> !lectura.getTimestamp().isBefore(limiteDesde))
                .filter(lectura -> !lectura.getTimestamp().isAfter(limiteHasta))
                .filter(lectura -> referenciaLlenoActualizada == null
                        || lectura.getTimestamp().isAfter(referenciaLlenoActualizada))
                .filter(lectura -> !lectura.isTieneMuestrasAtipicas())
                .sorted(Comparator.comparing(LecturaNivel::getTimestamp))
                .toList();
        if (lecturasDia.size() < 2) {
            return Optional.empty();
        }

        LecturaNivel primera = lecturasDia.get(0);
        LecturaNivel ultima = lecturasDia.get(lecturasDia.size() - 1);
        double minutos = Duration.between(
                primera.getTimestamp(), ultima.getTimestamp()).toMillis() / 60_000.0;
        if (minutos <= 0) {
            return Optional.empty();
        }
        double litrosConsumidos = 0;
        double minutosConMovimiento = 0;
        double minimoMovimientoPorIntervalo = Math.max(
                MOVIMIENTO_MINIMO_RITMO_DIARIO_LITROS,
                capacidadLitros * FRACCION_MINIMA_MOVIMIENTO_DIARIO);
        double minimoConsumoAcumulado = capacidadLitros * FRACCION_MINIMA_CONSUMO_DIARIO;
        for (int i = 1; i < lecturasDia.size(); i++) {
            LecturaNivel anterior = lecturasDia.get(i - 1);
            LecturaNivel siguiente = lecturasDia.get(i);
            long segundos = Duration.between(
                    anterior.getTimestamp(), siguiente.getTimestamp()).getSeconds();
            if (segundos <= 0 || segundos > MAX_SEPARACION_RITMO_DIARIO_MINUTOS * 60L) {
                continue;
            }
            double bajada = anterior.getVolumenLitros() - siguiente.getVolumenLitros();
            if (Double.isFinite(bajada) && bajada >= minimoMovimientoPorIntervalo) {
                litrosConsumidos += bajada;
                minutosConMovimiento += segundos / 60.0;
            }
        }
        if (litrosConsumidos < minimoConsumoAcumulado || minutosConMovimiento <= 0) {
            return Optional.empty();
        }
        double litrosPorMinuto = litrosConsumidos / minutosConMovimiento;
        if (!Double.isFinite(litrosPorMinuto) || litrosPorMinuto <= 0) {
            return Optional.empty();
        }
        double porcentaje = litrosPorMinuto / capacidadLitros * 100.0;
        return Optional.of(new TasaConsumo(porcentaje, litrosPorMinuto, minutosConMovimiento, true));
    }

    private Optional<TasaConsumo> calcularTasaConsumo(List<LecturaNivel> todas,
                                                       LecturaNivel actual,
                                                       LocalDateTime ts,
                                                       double alturaMaximaCm,
                                                       LocalDateTime referenciaLlenoActualizada,
                                                       double capacidadLitros) {
        if (!Double.isFinite(alturaMaximaCm) || alturaMaximaCm <= 0
                || !Double.isFinite(capacidadLitros) || capacidadLitros <= 0) {
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
            long segundos = Duration.between(
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
        double minutos = Duration.between(
                primera.getTimestamp(), ultima.getTimestamp()).toMillis() / 60_000.0;
        if (minutos <= 0) {
            return Optional.empty();
        }
        double nivelBajadoCm = primera.getNivelCm() - ultima.getNivelCm();
        double caidaMinimaCm = Math.max(CAIDA_MINIMA_CM, alturaMaximaCm * FRACCION_MINIMA_ALTURA);
        if (nivelBajadoCm < caidaMinimaCm) {
            return Optional.empty();
        }

        double litrosPorMinuto = (primera.getVolumenLitros() - ultima.getVolumenLitros()) / minutos;
        if (!Double.isFinite(litrosPorMinuto) || litrosPorMinuto <= 0) {
            return Optional.empty();
        }
        double porcentaje = nivelBajadoCm / alturaMaximaCm * 100.0 / minutos;
        boolean caidaContinua = true;
        for (int i = 1; i < segmento.size(); i++) {
            if (segmento.get(i).getNivelCm() > segmento.get(i - 1).getNivelCm() + CAIDA_MINIMA_CM) {
                caidaContinua = false;
                break;
            }
        }
        return Optional.of(new TasaConsumo(porcentaje, litrosPorMinuto, minutos, caidaContinua));
    }

    private PatronConsumo nuevoPatron(String tanqueId, String dia, String franja) {
        PatronConsumo patron = new PatronConsumo();
        patron.setTanqueId(tanqueId);
        patron.setDiaSemana(dia);
        patron.setFranja(franja);
        return patron;
    }

    private void actualizarPatron(PatronConsumo patron, double tasa) {
        int muestras = patron.getMuestrasTasaReferenciaNivel();
        double referencia = patron.getTasaReferenciaPctNivelPorMinuto();
        patron.setTasaReferenciaPctNivelPorMinuto(muestras == 0 || referencia <= 0
                ? tasa
                : referencia + PESO_ACTUALIZACION_PATRON * (tasa - referencia));
        patron.setMuestrasTasaReferenciaNivel(muestras + 1);
    }

    private boolean existeAlertaHoy(String tanqueId, LocalDate fecha, String rango, String tipo) {
        return alertaRepo.findByTanqueId(tanqueId).stream().anyMatch(alerta ->
                alerta.getTimestamp() != null && alerta.getTimestamp().toLocalDate().equals(fecha)
                        && tipo.equals(alerta.getTipo())
                        && alerta.getDescripcion() != null
                        && alerta.getDescripcion().contains(rango));
    }

    private Alerta nueva(String tanqueId,
                         String tipo,
                         String descripcion,
                         double desviacion,
                         double umbral,
                         LocalDateTime ts) {
        Alerta alerta = new Alerta();
        alerta.setTanqueId(tanqueId);
        alerta.setTipo(tipo);
        alerta.setDescripcion(descripcion);
        alerta.setDesviacionPorcentaje(Math.round(desviacion * 10.0) / 10.0);
        alerta.setUmbralAplicadoPct(umbral);
        alerta.setTimestamp(ts);
        alerta.setEstado("ACTIVA");
        alerta.setResuelta(false);
        return alerta;
    }

    private String diaSemana(LocalDateTime ts) {
        DayOfWeek dia = ts.getDayOfWeek();
        if (dia == DayOfWeek.MONDAY) return "LUNES";
        if (dia == DayOfWeek.TUESDAY) return "MARTES";
        if (dia == DayOfWeek.WEDNESDAY) return "MIERCOLES";
        if (dia == DayOfWeek.THURSDAY) return "JUEVES";
        if (dia == DayOfWeek.FRIDAY) return "VIERNES";
        if (dia == DayOfWeek.SATURDAY) return "SABADO";
        return "DOMINGO";
    }

    private String rangoFranja(FranjaHoraria franja) {
        if (franja == FranjaHoraria.MADRUGADA) return "00:00-06:00";
        if (franja == FranjaHoraria.MANANA) return "06:00-12:00";
        if (franja == FranjaHoraria.TARDE) return "12:00-18:00";
        return "18:00-24:00";
    }

    private String redPorcentaje(double valor) {
        return String.format(Locale.ROOT, "%.0f", valor);
    }

    private String redTasa(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }

    private String redLitros(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor);
    }

    private String red(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor);
    }

    private enum Fase {
        ARRANQUE_EN_FRIO,
        INTRA_DIA,
        HISTORICO
    }

    private record TasaConsumo(double porcentajeAlturaPorMinuto,
                               double litrosPorMinuto,
                               double duracionMinutos,
                               boolean caidaContinua) {
    }

    private record CandidatoAnomalia(String descripcion,
                                     double desviacionPorcentaje,
                                     double umbralAplicado) {
    }
}

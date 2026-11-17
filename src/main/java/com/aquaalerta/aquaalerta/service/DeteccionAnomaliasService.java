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
import java.time.LocalTime;
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
    private static final int DURACION_ARRANQUE_EN_FRIO_HORAS = 1;
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
    private static final LocalTime INICIO_VENTANA_NOCTURNA = LocalTime.of(1, 0);
    private static final LocalTime FIN_VENTANA_NOCTURNA = LocalTime.of(5, 0);
    private static final int MINUTOS_BLOQUE_PROMEDIO = 5;
    private static final int MIN_LECTURAS_POR_BLOQUE = 5;
    private static final double CAIDA_NOCTURNA_MINIMA_CM = 1.0;
    private static final int VENTANA_FUGA_LENTA_MINUTOS = 30;
    private static final double CAIDA_FUGA_LENTA_MINIMA_CM = 1.0;
    private static final int CANTIDAD_BLOQUES_FUGA_LENTA = 6;
    private static final int DESCENSOS_MINIMOS_TENDENCIA = 3;
    private static final double PAUSA_MAXIMA_TENDENCIA_CM = 0.3;
    private static final double PASO_DESCENDENTE_MINIMO_CM = 0.05;
    private static final int VENTANA_CAIDA_BRUSCA_MINUTOS = 2;
    private static final int LECTURAS_CAIDA_BRUSCA_REQUERIDAS = 3;
    private static final long SEPARACION_CAIDA_BRUSCA_MAXIMA_SEGUNDOS = 60;
    private static final double CAIDA_BRUSCA_MINIMA_CM = 3.0;
    private static final double RUIDO_MAXIMO_CAIDA_BRUSCA_CM = 0.2;

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

        CandidatoAnomalia reglaAdicional = evaluarReglasAdicionales(
                tanque, lecturas, guardada, ts);
        Optional<TasaConsumo> tasaActual = calcularTasaConsumo(
                lecturas, guardada, ts, tanque.getAlturaMaxCm(),
                tanque.getReferenciaLlenoActualizada(), tanque.getVolumenMax());
        if (tasaActual.isEmpty()) {
            if (reglaAdicional != null) {
                confirmarAnomalia(tanque, guardada, patron, reglaAdicional,
                        ultimaEvaluacion, ts, rango);
            } else {
                reiniciarConfirmacionAnomalia(patron);
            }
            patronRepo.save(patron);
            return;
        }

        TasaConsumo tasa = tasaActual.get();
        Optional<TasaConsumo> ritmoDiario = calcularRitmoDiario(
                lecturas, ts, tanque.getReferenciaLlenoActualizada(), tanque.getVolumenMax());
        Fase fase = determinarFase(primeraLectura, ts);
        CandidatoAnomalia candidato = reglaAdicional != null
                ? reglaAdicional
                : evaluarFase(fase, tanque, patron, tasa, ritmoDiario, dia, rango);

        if (candidato != null) {
            confirmarAnomalia(tanque, guardada, patron, candidato,
                    ultimaEvaluacion, ts, rango);
        } else {
            reiniciarConfirmacionAnomalia(patron);
            if (fase == Fase.HISTORICO) {
                evaluarInformativo(tanque, patron, tasa, ts, dia, rango);
            }
            if (tasa.porcentajeAlturaPorMinuto() > 0) {
                actualizarPatron(patron, tasa.porcentajeAlturaPorMinuto());
            }
        }
        patronRepo.save(patron);
    }

    private CandidatoAnomalia evaluarReglasAdicionales(Tanque tanque,
                                                        List<LecturaNivel> lecturas,
                                                        LecturaNivel actual,
                                                        LocalDateTime ts) {
        List<LecturaNivel> validas = lecturasValidas(
                lecturas, actual, ts, tanque.getReferenciaLlenoActualizada());
        CandidatoAnomalia caidaBrusca = evaluarCaidaBrusca(validas, ts);
        if (caidaBrusca != null) {
            return caidaBrusca;
        }
        CandidatoAnomalia fugaLenta = evaluarFugaLenta(validas, ts);
        if (fugaLenta != null) {
            return fugaLenta;
        }
        return evaluarCaidaNocturna(validas, ts);
    }

    private CandidatoAnomalia evaluarCaidaNocturna(List<LecturaNivel> lecturas,
                                                    LocalDateTime ts) {
        LocalDateTime inicio = ts.toLocalDate().atTime(INICIO_VENTANA_NOCTURNA);
        LocalDateTime fin = ts.toLocalDate().atTime(FIN_VENTANA_NOCTURNA);
        LocalDateTime finBloqueInicial = inicio.plusMinutes(MINUTOS_BLOQUE_PROMEDIO);
        if (ts.isBefore(finBloqueInicial.plusMinutes(MINUTOS_BLOQUE_PROMEDIO))
                || !ts.isBefore(fin)) {
            return null;
        }

        Optional<Double> nivelInicial = promedioNivel(
                lecturas, inicio, finBloqueInicial, MIN_LECTURAS_POR_BLOQUE);
        Optional<Double> nivelActual = promedioNivel(
                lecturas, ts.minusMinutes(MINUTOS_BLOQUE_PROMEDIO),
                ts.plusNanos(1), MIN_LECTURAS_POR_BLOQUE);
        if (nivelInicial.isEmpty() || nivelActual.isEmpty()) {
            return null;
        }

        double caidaCm = nivelInicial.get() - nivelActual.get();
        if (caidaCm <= CAIDA_NOCTURNA_MINIMA_CM) {
            return null;
        }
        return new CandidatoAnomalia(
                "FUGA_NOCTURNA",
                "cm",
                "Regla nocturna (01:00-05:00): caída promedio de "
                        + red(caidaCm) + " cm desde el inicio de la ventana.",
                caidaCm,
                CAIDA_NOCTURNA_MINIMA_CM);
    }

    private CandidatoAnomalia evaluarFugaLenta(List<LecturaNivel> lecturas,
                                               LocalDateTime ts) {
        LocalDateTime inicio = ts.minusMinutes(VENTANA_FUGA_LENTA_MINUTOS);
        List<Double> promedios = new ArrayList<>();
        for (int bloque = 0; bloque < CANTIDAD_BLOQUES_FUGA_LENTA; bloque++) {
            LocalDateTime desde = inicio.plusMinutes((long) bloque * MINUTOS_BLOQUE_PROMEDIO);
            LocalDateTime hasta = bloque == CANTIDAD_BLOQUES_FUGA_LENTA - 1
                    ? ts.plusNanos(1)
                    : desde.plusMinutes(MINUTOS_BLOQUE_PROMEDIO);
            Optional<Double> promedio = promedioNivel(
                    lecturas, desde, hasta, MIN_LECTURAS_POR_BLOQUE);
            if (promedio.isEmpty()) {
                return null;
            }
            promedios.add(promedio.get());
        }

        int descensos = 0;
        for (int i = 1; i < promedios.size(); i++) {
            double cambio = promedios.get(i - 1) - promedios.get(i);
            if (cambio > PASO_DESCENDENTE_MINIMO_CM) {
                descensos++;
            } else if (cambio < -PAUSA_MAXIMA_TENDENCIA_CM) {
                return null;
            }
        }
        double caidaCm = promedios.getFirst() - promedios.getLast();
        if (caidaCm <= CAIDA_FUGA_LENTA_MINIMA_CM
                || descensos < DESCENSOS_MINIMOS_TENDENCIA) {
            return null;
        }
        return new CandidatoAnomalia(
                "FUGA_LENTA",
                "cm",
                "Fuga lenta: tendencia descendente de " + red(caidaCm)
                        + " cm en los últimos " + VENTANA_FUGA_LENTA_MINUTOS
                        + " minutos, calculada con promedios de bloques.",
                caidaCm,
                CAIDA_FUGA_LENTA_MINIMA_CM);
    }

    private CandidatoAnomalia evaluarCaidaBrusca(List<LecturaNivel> lecturas,
                                                  LocalDateTime ts) {
        LocalDateTime inicio = ts.minusMinutes(VENTANA_CAIDA_BRUSCA_MINUTOS);
        List<LecturaNivel> recientes = lecturas.stream()
                .filter(lectura -> !lectura.getTimestamp().isBefore(inicio))
                .filter(lectura -> !lectura.getTimestamp().isAfter(ts))
                .toList();
        if (recientes.size() < LECTURAS_CAIDA_BRUSCA_REQUERIDAS) {
            return null;
        }

        int primerIndice = recientes.size() - LECTURAS_CAIDA_BRUSCA_REQUERIDAS;
        List<LecturaNivel> segmento = recientes.subList(
                primerIndice, recientes.size());
        for (int i = 1; i < segmento.size(); i++) {
            long segundos = Duration.between(
                    segmento.get(i - 1).getTimestamp(),
                    segmento.get(i).getTimestamp()).getSeconds();
            double bajadaCm = segmento.get(i - 1).getNivelCm()
                    - segmento.get(i).getNivelCm();
            if (segundos <= 0
                    || segundos > SEPARACION_CAIDA_BRUSCA_MAXIMA_SEGUNDOS
                    || bajadaCm <= RUIDO_MAXIMO_CAIDA_BRUSCA_CM) {
                return null;
            }
        }

        double caidaCm = segmento.getFirst().getNivelCm()
                - segmento.getLast().getNivelCm();
        if (caidaCm < CAIDA_BRUSCA_MINIMA_CM) {
            return null;
        }
        return new CandidatoAnomalia(
                "CAIDA_BRUSCA",
                "cm",
                "Caída brusca: descenso progresivo de " + red(caidaCm)
                        + " cm confirmado en " + segmento.size()
                        + " lecturas consecutivas.",
                caidaCm,
                CAIDA_BRUSCA_MINIMA_CM);
    }

    private Optional<Double> promedioNivel(List<LecturaNivel> lecturas,
                                            LocalDateTime desde,
                                            LocalDateTime hasta,
                                            int minimoLecturas) {
        List<LecturaNivel> bloque = lecturas.stream()
                .filter(lectura -> !lectura.getTimestamp().isBefore(desde))
                .filter(lectura -> lectura.getTimestamp().isBefore(hasta))
                .toList();
        if (bloque.size() < minimoLecturas) {
            return Optional.empty();
        }
        return Optional.of(bloque.stream()
                .mapToDouble(LecturaNivel::getNivelCm)
                .average()
                .orElseThrow());
    }

    private List<LecturaNivel> lecturasValidas(List<LecturaNivel> todas,
                                                LecturaNivel actual,
                                                LocalDateTime ts,
                                                LocalDateTime referenciaLlenoActualizada) {
        List<LecturaNivel> validas = new ArrayList<>(todas.stream()
                .filter(lectura -> lectura.getTimestamp() != null)
                .filter(lectura -> !lectura.getTimestamp().isAfter(ts))
                .filter(lectura -> referenciaLlenoActualizada == null
                        || lectura.getTimestamp().isAfter(referenciaLlenoActualizada))
                .filter(lectura -> !lectura.isTieneMuestrasAtipicas())
                .filter(lectura -> Double.isFinite(lectura.getNivelCm())
                        && lectura.getNivelCm() >= 0)
                .toList());
        boolean incluyeActual = validas.stream().anyMatch(lectura ->
                actual.getId() == null
                        ? lectura == actual
                        : actual.getId().equals(lectura.getId()));
        if (!incluyeActual && actual.getTimestamp() != null
                && !actual.isTieneMuestrasAtipicas()
                && Double.isFinite(actual.getNivelCm())
                && actual.getNivelCm() >= 0
                && (referenciaLlenoActualizada == null
                || actual.getTimestamp().isAfter(referenciaLlenoActualizada))) {
            validas.add(actual);
        }
        validas.sort(Comparator.comparing(LecturaNivel::getTimestamp));
        return validas;
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
            // Fase 0: durante la primera hora se aplican reglas físicas y de fuga independientes.
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
                    "COMPARACION_INTRADIA",
                    "%",
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
        return new CandidatoAnomalia(
                "PATRON_HISTORICO", "%", descripcion, desviacion, umbral);
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
                    desviacion, margen, ts, "PATRON_INFORMATIVO", "%"));
        }
    }

    private void confirmarAnomalia(Tanque tanque,
                                   LecturaNivel guardada,
                                   PatronConsumo patron,
                                   CandidatoAnomalia candidato,
                                   LocalDateTime ultimaEvaluacion,
                                   LocalDateTime ts,
                                   String rango) {
        boolean mismaRegla = candidato.reglaId().equals(patron.getReglaAnomalaEnConfirmacion());
        boolean consecutiva = mismaRegla && ultimaEvaluacion != null
                && Duration.between(ultimaEvaluacion, ts).getSeconds()
                <= MAX_SEPARACION_MINUTOS * 60L;
        patron.setEvaluacionesAnomalasConsecutivas(consecutiva
                ? Math.min(EVALUACIONES_CONSECUTIVAS_REQUERIDAS,
                        patron.getEvaluacionesAnomalasConsecutivas() + 1)
                : 1);
        patron.setReglaAnomalaEnConfirmacion(candidato.reglaId());
        if (patron.getEvaluacionesAnomalasConsecutivas()
                < EVALUACIONES_CONSECUTIVAS_REQUERIDAS) {
            return;
        }

        guardada.setEsAnomalia(true);
        lecturaRepo.save(guardada);
        if (!existeAlertaReglaHoy(tanque.getId(), ts.toLocalDate(),
                "ANOMALIA_CONSUMO", candidato.reglaId())) {
            alertaRepo.save(nueva(tanque.getId(), "ANOMALIA_CONSUMO",
                    candidato.descripcion(), candidato.desviacionPorcentaje(),
                    candidato.umbralAplicado(), ts, candidato.reglaId(),
                    candidato.unidadMedida()));
        }
    }

    private void reiniciarConfirmacionAnomalia(PatronConsumo patron) {
        patron.setEvaluacionesAnomalasConsecutivas(0);
        patron.setReglaAnomalaEnConfirmacion(null);
    }

    private boolean superaReglaFisica(TasaConsumo tasa) {
        return tasa.caidaContinua()
                && tasa.duracionMinutos() >= DURACION_FISICA_MINIMA_MINUTOS
                && tasa.litrosPorMinuto() > VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO;
    }

    private CandidatoAnomalia candidatoFisico(TasaConsumo tasa, String fase, String rango) {
        return new CandidatoAnomalia(
                "FISICA_" + fase,
                "L/min",
                fase + ": posible fuga; caída sostenida de "
                        + redLitros(tasa.litrosPorMinuto()) + " L/min durante "
                        + red(tasa.duracionMinutos()) + " minutos (límite: "
                        + redLitros(VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO)
                        + " L/min) en la franja " + rango + ".",
                tasa.litrosPorMinuto(),
                VELOCIDAD_FISICA_MINIMA_LITROS_MINUTO);
    }

    private Fase determinarFase(LocalDateTime primeraLectura, LocalDateTime ts) {
        long minutosDesdeInicio = Math.max(0, Duration.between(primeraLectura, ts).toMinutes());
        if (minutosDesdeInicio < Duration.ofHours(DURACION_ARRANQUE_EN_FRIO_HORAS).toMinutes()) {
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

    private boolean existeAlertaReglaHoy(String tanqueId,
                                         LocalDate fecha,
                                         String tipo,
                                         String reglaId) {
        return alertaRepo.findByTanqueId(tanqueId).stream().anyMatch(alerta ->
                alerta.getTimestamp() != null
                        && alerta.getTimestamp().toLocalDate().equals(fecha)
                        && tipo.equals(alerta.getTipo())
                        && reglaId.equals(alerta.getReglaId()));
    }

    private Alerta nueva(String tanqueId,
                         String tipo,
                         String descripcion,
                         double desviacion,
                         double umbral,
                         LocalDateTime ts,
                         String reglaId,
                         String unidadMedida) {
        Alerta alerta = new Alerta();
        alerta.setTanqueId(tanqueId);
        alerta.setTipo(tipo);
        alerta.setDescripcion(descripcion);
        alerta.setDesviacionPorcentaje(Math.round(desviacion * 10.0) / 10.0);
        alerta.setUmbralAplicadoPct(umbral);
        alerta.setReglaId(reglaId);
        alerta.setUnidadMedida(unidadMedida);
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

    private record CandidatoAnomalia(String reglaId,
                                     String unidadMedida,
                                     String descripcion,
                                     double desviacionPorcentaje,
                                     double umbralAplicado) {
    }
}

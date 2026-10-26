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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DeteccionAnomaliasService {

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

    // PROTECCION 1 (recomendacion profe): revision de extremos.
    // Si el volumen cae mas del 30% respecto a la lectura anterior
    // en un solo salto, se marca como sospechosa y se evalua aparte.
    private boolean esSaltoSospechoso(String tanqueId, LecturaNivel actual) {
        List<LecturaNivel> todas = lecturaRepo.findByTanqueIdOrderByTimestampDesc(tanqueId);
        LecturaNivel anterior = todas.stream()
                .filter(l -> l.getTimestamp() != null && actual.getTimestamp() != null)
                .filter(l -> l.getTimestamp().isBefore(actual.getTimestamp()))
                .filter(l -> !l.getId().equals(actual.getId()))
                .findFirst()
                .orElse(null);
        if (anterior == null || anterior.getVolumenLitros() <= 0) {
            return false;
        }
        double caida = (anterior.getVolumenLitros() - actual.getVolumenLitros())
                / anterior.getVolumenLitros();
        return caida > 0.30;
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
        boolean saltoSospechoso = esSaltoSospechoso(tanque.getId(), guardada);
        double actual = consumoFranja(tanque.getId(), ts);
        Optional<PatronConsumo> patron = patronRepo
                .findByTanqueIdAndDiaSemanaAndFranja(tanque.getId(), dia, franja.getCodigo());
        if (patron.isEmpty() || patron.get().getConsumoReferenciaLitros() <= 0) {
            acumularBase(tanque.getId(), dia, franja.getCodigo(), actual, ts);
            if (saltoSospechoso && !existeHoy(tanque.getId(), ts.toLocalDate(), rango)) {
                alertaRepo.save(nueva(tanque.getId(), "INFORMATIVO",
                        "Salto brusco de nivel en la franja " + rango
                                + ". Lectura marcada como sospechosa, pendiente de mas datos.",
                        0, tanque.getMargenInformativoPct(), ts));
            }
            return;
        }
        double ref = patron.get().getConsumoReferenciaLitros();
        double desv = ((actual - ref) / ref) * 100.0;
        double margen = tanque.getMargenInformativoPct();
        double umbral = tanque.getUmbralAnomaliaPct();
        String desc = "Consumo de " + red(actual) + " L en la franja " + rango
                + ", " + red(desv) + "% sobre el promedio habitual del "
                + dia.toLowerCase(Locale.ROOT) + " (" + red(ref) + " L)";
        int nFranja = contarLecturasFranja(tanque.getId(), ts);
        boolean pocosDatos = nFranja < 3;
        if (actual < 25) {
            acumularBase(tanque.getId(), dia, franja.getCodigo(), actual, ts);
            return;
        }
        if (desv > umbral && !pocosDatos) {
            if (existeHoy(tanque.getId(), ts.toLocalDate(), rango)) {
                return;
            }
            alertaRepo.save(nueva(tanque.getId(), "ANOMALIA_CONSUMO", desc, desv, umbral, ts));
            guardada.setEsAnomalia(true);
            lecturaRepo.save(guardada);
        } else if (desv > margen) {
            if (existeHoy(tanque.getId(), ts.toLocalDate(), rango)) {
                return;
            }
            // Si hay pocos datos o salto sospechoso aislado, se queda en INFORMATIVO.
            alertaRepo.save(nueva(tanque.getId(), "INFORMATIVO", desc, desv, margen, ts));
        } else {
            acumularBase(tanque.getId(), dia, franja.getCodigo(), actual, ts);
        }
    }

    private double consumoFranja(String tanqueId, LocalDateTime ts) {
        FranjaHoraria f = FranjaHoraria.de(ts);
        LocalDate fecha = ts.toLocalDate();
        LocalDateTime ini = LocalDateTime.of(fecha, LocalTime.of(horaIni(f), 0));
        LocalDateTime fin = finFranja(fecha, f);
        List<LecturaNivel> todas = lecturaRepo.findByTanqueIdOrderByTimestampDesc(tanqueId);
        List<LecturaNivel> lista = todas.stream()
                .filter(l -> l.getTimestamp() != null)
                .filter(l -> !l.getTimestamp().isAfter(ts))
                .sorted(Comparator.comparing(LecturaNivel::getTimestamp))
                .toList();
        double consumo = 0;
        for (int i = 1; i < lista.size(); i++) {
            LocalDateTime tAnt = lista.get(i - 1).getTimestamp();
            LocalDateTime tAct = lista.get(i).getTimestamp();
            if (tAct.isBefore(ini) || tAct.isAfter(fin)) {
                continue;
            }
            if (tAnt.isBefore(ini.minusHours(12))) {
                continue;
            }
            long minutos = java.time.Duration.between(tAnt, tAct).toMinutes();
            if (minutos > 120) {
                continue;
            }
            double diff = lista.get(i - 1).getVolumenLitros() - lista.get(i).getVolumenLitros();
            if (diff > 0) {
                consumo += diff;
            }
        }
        return consumo;
    }

    private int contarLecturasFranja(String tanqueId, LocalDateTime ts) {
        FranjaHoraria f = FranjaHoraria.de(ts);
        LocalDate fecha = ts.toLocalDate();
        LocalDateTime ini = LocalDateTime.of(fecha, LocalTime.of(horaIni(f), 0));
        LocalDateTime fin = finFranja(fecha, f);
        return (int) lecturaRepo.findByTanqueIdOrderByTimestampDesc(tanqueId).stream()
                .filter(l -> l.getTimestamp() != null)
                .filter(l -> !l.getTimestamp().isAfter(ts))
                .filter(l -> !l.getTimestamp().isBefore(ini) && !l.getTimestamp().isAfter(fin))
                .count();
    }

    private void acumularBase(String tanqueId, String dia, String franja, double actual, LocalDateTime ts) {
        Optional<PatronConsumo> opt = patronRepo.findByTanqueIdAndDiaSemanaAndFranja(tanqueId, dia, franja);
        PatronConsumo p = opt.orElseGet(PatronConsumo::new);
        p.setTanqueId(tanqueId);
        p.setDiaSemana(dia);
        p.setFranja(franja);
        p.setConsumoReferenciaLitros(Math.max(actual, p.getConsumoReferenciaLitros()));
        int sem = p.getSemanasConsideradas() + 1;
        if (sem < 1) sem = 1;
        if (sem > 4) sem = 4;
        p.setSemanasConsideradas(sem);
        p.setActualizado(ts);
        patronRepo.save(p);
    }

    private LocalDate primeraFecha(String tanqueId) {
        return lecturaRepo.findByTanqueIdOrderByTimestampDesc(tanqueId).stream()
                .filter(l -> l.getTimestamp() != null)
                .map(l -> l.getTimestamp().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private boolean existeHoy(String tanqueId, LocalDate fecha, String rango) {
        return alertaRepo.findByTanqueId(tanqueId).stream().anyMatch(a ->
                a.getTimestamp() != null && a.getTimestamp().toLocalDate().equals(fecha)
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

    private int horaIni(FranjaHoraria f) {
        if (f == FranjaHoraria.MADRUGADA) return 0;
        if (f == FranjaHoraria.MANANA) return 6;
        if (f == FranjaHoraria.TARDE) return 12;
        return 18;
    }

    private LocalDateTime finFranja(LocalDate fecha, FranjaHoraria f) {
        if (f == FranjaHoraria.MADRUGADA) return LocalDateTime.of(fecha, LocalTime.of(6, 0));
        if (f == FranjaHoraria.MANANA) return LocalDateTime.of(fecha, LocalTime.of(12, 0));
        if (f == FranjaHoraria.TARDE) return LocalDateTime.of(fecha, LocalTime.of(18, 0));
        return fecha.plusDays(1).atStartOfDay();
    }

    private String rangoFranja(FranjaHoraria f) {
        if (f == FranjaHoraria.MADRUGADA) return "00:00-06:00";
        if (f == FranjaHoraria.MANANA) return "06:00-12:00";
        if (f == FranjaHoraria.TARDE) return "12:00-18:00";
        return "18:00-24:00";
    }

    private long red(double v) {
        return Math.round(v);
    }
}

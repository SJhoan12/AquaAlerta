package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.dto.TanqueEstadoDto;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class DashboardService {

    private static final int CANTIDAD_LECTURAS_RECIENTES = 10;
    private static final int MINUTOS_SENSOR_CONECTADO = 5;

    private final TanqueRepository tanqueRepository;
    private final LecturaNivelRepository lecturaNivelRepository;

    public DashboardService(TanqueRepository tanqueRepository,
                            LecturaNivelRepository lecturaNivelRepository) {
        this.tanqueRepository = tanqueRepository;
        this.lecturaNivelRepository = lecturaNivelRepository;
    }

    public List<TanqueEstadoDto> estadosTanques() {
        List<TanqueEstadoDto> out = new ArrayList<>();
        for (Tanque t : tanqueRepository.findAll()) {
            List<LecturaNivel> ultimas =
                    lecturaNivelRepository.findByTanqueIdOrderByTimestampDesc(t.getId());
            double nivel = 0, vol = 0;
            if (!ultimas.isEmpty()) {
                nivel = ultimas.get(0).getNivelCm();
                vol = ultimas.get(0).getVolumenLitros();
            }
            double max = t.getVolumenMax() > 0 ? t.getVolumenMax() : 1;
            double pct = Math.min(100.0, Math.max(0.0, vol / max * 100.0));
            String estado = nivel <= t.getUmbralMinimo() ? "BAJO" : "NORMAL";
            String css = "BAJO".equals(estado) ? "danger" : "success";
            out.add(new TanqueEstadoDto(t.getId(), t.getNombre(), nivel, vol,
                    t.getVolumenMax(), pct, estado, css,
                    t.getNumeroPersonas(),
                    t.calcularGastoDiarioReferencial(), t.obtenerTemporadaActual(),
                    t.isModoVigilancia()));
        }
        return out;
    }

    public ResumenLecturas resumenLecturas(String tanqueId) {
        List<LecturaNivel> todas = lecturaNivelRepository
                .findByTanqueIdOrderByTimestampDesc(tanqueId);
        List<LecturaNivel> recientes = todas.stream()
                .limit(CANTIDAD_LECTURAS_RECIENTES)
                .toList();
        LocalDateTime ultimaSincronizacion = todas.stream()
                .map(LecturaNivel::getTimestamp)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        boolean conectado = ultimaSincronizacion != null
                && !ultimaSincronizacion.isAfter(ahora)
                && !ultimaSincronizacion.isBefore(
                        ahora.minus(Duration.ofMinutes(MINUTOS_SENSOR_CONECTADO)));
        return new ResumenLecturas(recientes, todas.size(), ultimaSincronizacion, conectado);
    }

    public record ResumenLecturas(List<LecturaNivel> ultimas,
                                  int cantidadLecturas,
                                  LocalDateTime ultimaSincronizacion,
                                  boolean sensorConectado) {
    }
}


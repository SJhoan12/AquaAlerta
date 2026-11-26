package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TanqueService {

    private final TanqueRepository tanqueRepository;
    private final LecturaNivelRepository lecturaNivelRepository;

    public TanqueService(TanqueRepository tanqueRepository,
                         LecturaNivelRepository lecturaNivelRepository) {
        this.tanqueRepository = tanqueRepository;
        this.lecturaNivelRepository = lecturaNivelRepository;
    }

    public Tanque guardar(Tanque tanque) {
        if (!Double.isFinite(tanque.getAlturaMaxCm()) || tanque.getAlturaMaxCm() <= 0) {
            throw new IllegalArgumentException("Ingresa una altura mayor que cero.");
        }
        if (!Double.isFinite(tanque.getVolumenMax()) || tanque.getVolumenMax() <= 0) {
            throw new IllegalArgumentException("Ingresa la capacidad en litros indicada por el fabricante.");
        }
        if (!Tanque.esCantidadPersonasValida(tanque.getNumeroPersonas())) {
            throw new IllegalArgumentException("El número de personas debe estar entre 1 y 30.");
        }
        tanque.setNumeroPersonas(tanque.getNumeroPersonas());
        tanque.setModoVigilancia(tanque.isModoVigilancia());
        if (tanque.getId() == null || tanque.getId().isBlank()) {
            tanque.setId(null);
        } else {
            Tanque anterior = tanqueRepository.findById(tanque.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el tanque que intentas editar."));
            tanque.setUsuarioId(anterior.getUsuarioId());
            tanque.setModoVigilancia(anterior.isModoVigilancia());
            if (Double.compare(anterior.getAlturaMaxCm(), tanque.getAlturaMaxCm()) == 0) {
                tanque.setDistanciaLlenoCm(anterior.getDistanciaLlenoCm());
                tanque.setReferenciaLlenoActualizada(anterior.getReferenciaLlenoActualizada());
            }
        }
        return tanqueRepository.save(tanque);
    }

    public void registrarLleno(String tanqueId) {
        Tanque tanque = tanqueRepository.findById(tanqueId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el tanque seleccionado."));
        LecturaNivel lectura = lecturaNivelRepository
                .findTopByTanqueIdOrderByTimestampDesc(tanqueId)
                .orElseThrow(() -> new IllegalStateException(
                        "Aún no hay lecturas de este tanque. Enciende el sensor y vuelve a intentarlo."));
        LocalDateTime ahora = LocalDateTime.now();
        if (lectura.getTimestamp() == null
                || lectura.getTimestamp().isBefore(ahora.minusMinutes(5))
                || lectura.getTimestamp().isAfter(ahora)
                || lectura.isTieneMuestrasAtipicas()) {
            throw new IllegalStateException(
                    "La última lectura no es reciente o no es confiable. Espera una lectura válida y vuelve a intentarlo.");
        }
        tanque.setDistanciaLlenoCm(lectura.getDistanciaCm());
        tanque.setReferenciaLlenoActualizada(ahora);
        tanqueRepository.save(tanque);
    }

    public Tanque alternarModoVigilancia(String tanqueId) {
        Tanque tanque = tanqueRepository.findById(tanqueId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el tanque seleccionado."));
        tanque.setModoVigilancia(!tanque.isModoVigilancia());
        return tanqueRepository.save(tanque);
    }

    public List<Tanque> listarTodos() {
        return tanqueRepository.findAll();
    }

    public Optional<Tanque> buscarPorId(String id) {
        return tanqueRepository.findById(id);
    }

    public void eliminarPorId(String id) {
        tanqueRepository.deleteById(id);
    }
}

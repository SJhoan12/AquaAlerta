package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import com.aquaalerta.aquaalerta.service.CalculoVolumenService;
import com.aquaalerta.aquaalerta.service.DeteccionAnomaliasService;
import com.aquaalerta.aquaalerta.service.ProcesamientoMuestrasService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class LecturaController {

    private final TanqueRepository tanqueRepository;
    private final LecturaNivelRepository lecturaNivelRepository;
    private final DeteccionAnomaliasService deteccionService;
    private final CalculoVolumenService calculoVolumenService;
    private final ProcesamientoMuestrasService procesamientoMuestrasService;

    public LecturaController(TanqueRepository tanqueRepository,
                             LecturaNivelRepository lecturaNivelRepository,
                             DeteccionAnomaliasService deteccionService,
                             CalculoVolumenService calculoVolumenService,
                             ProcesamientoMuestrasService procesamientoMuestrasService) {
        this.tanqueRepository = tanqueRepository;
        this.lecturaNivelRepository = lecturaNivelRepository;
        this.deteccionService = deteccionService;
        this.calculoVolumenService = calculoVolumenService;
        this.procesamientoMuestrasService = procesamientoMuestrasService;
    }

    public static class LecturaRequest {
        private String tanqueId;
        private String sensorId;
        private Double distanciaCm;
        private List<Double> distanciasCm;
        private LocalDateTime timestamp;

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getTanqueId() {
            return tanqueId;
        }

        public void setTanqueId(String tanqueId) {
            this.tanqueId = tanqueId;
        }

        public String getSensorId() {
            return sensorId;
        }

        public void setSensorId(String sensorId) {
            this.sensorId = sensorId;
        }

        public Double getDistanciaCm() {
            return distanciaCm;
        }

        public void setDistanciaCm(Double distanciaCm) {
            this.distanciaCm = distanciaCm;
        }

        public List<Double> getDistanciasCm() {
            return distanciasCm;
        }

        public void setDistanciasCm(List<Double> distanciasCm) {
            this.distanciasCm = distanciasCm;
        }
    }

    @PostMapping("/api/lecturas")
    public ResponseEntity<?> recibirLectura(@RequestBody LecturaRequest request) {
        if (request.getTanqueId() == null || request.getTanqueId().isBlank()) {
            return ResponseEntity.badRequest().body("Debe especificar tanqueId.");
        }

        Tanque tanque = tanqueRepository.findById(request.getTanqueId()).orElse(null);
        if (tanque == null) {
            return new ResponseEntity<>("Tanque no encontrado con id: " + request.getTanqueId(), HttpStatus.NOT_FOUND);
        }

        ProcesamientoMuestrasService.ResultadoMuestras resultado;
        try {
            resultado = procesamientoMuestrasService.procesar(
                    request.getDistanciasCm(), request.getDistanciaCm(), tanque.getAlturaMaxCm());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }

        double nivelCm = tanque.getAlturaMaxCm() - resultado.distanciaMedianaCm();
        double volumenLitros = calculoVolumenService.calcularVolumenLitros(
                nivelCm, tanque.getDiametroCm());

        LecturaNivel lectura = new LecturaNivel();
        lectura.setTanqueId(request.getTanqueId());
        lectura.setSensorId(request.getSensorId());
        lectura.setDistanciaCm(resultado.distanciaMedianaCm());
        lectura.setDistanciasOriginalesCm(resultado.distanciasOriginalesCm());
        lectura.setTieneMuestrasAtipicas(resultado.tieneMuestrasAtipicas());
        lectura.setNivelCm(nivelCm);
        lectura.setVolumenLitros(volumenLitros);
        lectura.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now());
        lectura.setEsAnomalia(false);

        LecturaNivel guardada = lecturaNivelRepository.save(lectura);
        deteccionService.evaluar(tanque, guardada);
        LecturaNivel final_ = lecturaNivelRepository.findById(guardada.getId()).orElse(guardada);
        return new ResponseEntity<>(final_, HttpStatus.CREATED);
    }
}

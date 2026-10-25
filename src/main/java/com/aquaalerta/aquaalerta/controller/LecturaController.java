package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import com.aquaalerta.aquaalerta.service.CalculoVolumenService;
import com.aquaalerta.aquaalerta.service.DeteccionAnomaliasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class LecturaController {

    @Autowired
    private TanqueRepository tanqueRepository;

    @Autowired
    private LecturaNivelRepository lecturaNivelRepository;

    @Autowired
    private DeteccionAnomaliasService deteccionService;

    @Autowired
    private CalculoVolumenService calculoVolumenService;

    public static class LecturaRequest {
        private String tanqueId;
        private String sensorId;
        private double distanciaCm;
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

        public double getDistanciaCm() {
            return distanciaCm;
        }

        public void setDistanciaCm(double distanciaCm) {
            this.distanciaCm = distanciaCm;
        }
    }

    @PostMapping("/api/lecturas")
    public ResponseEntity<?> recibirLectura(@RequestBody LecturaRequest request) {
        Tanque tanque = tanqueRepository.findById(request.getTanqueId()).orElse(null);
        if (tanque == null) {
            return new ResponseEntity<>("Tanque no encontrado con id: " + request.getTanqueId(), HttpStatus.NOT_FOUND);
        }

        double nivelCm = tanque.getAlturaMaxCm() - request.getDistanciaCm();
        if (nivelCm < 0) {
            nivelCm = 0;
        }

        double volumenLitros = calculoVolumenService.calcularVolumenLitros(
                nivelCm, tanque.getDiametroCm());

        LecturaNivel lectura = new LecturaNivel();
        lectura.setTanqueId(request.getTanqueId());
        lectura.setSensorId(request.getSensorId());
        lectura.setDistanciaCm(request.getDistanciaCm());
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

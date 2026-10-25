package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU06 + CU07: el ESP32 registra la lectura (RF06) y el backend
 * calcula/persiste nivel y volumen. Comunicación HTTP + WiFi (12.2).
 * El ESP32 envía mediana de distancia + sensorId.
 */
@RestController
@RequestMapping("/api/lecturas")
public class SensorController {

    private final LecturaNivelRepository lecturaNivelRepository;

    public SensorController(LecturaNivelRepository lecturaNivelRepository) {
        this.lecturaNivelRepository = lecturaNivelRepository;
    }

    @GetMapping
    public List<LecturaNivel> listarLecturasJson() {
        return lecturaNivelRepository.findAll();
    }
}


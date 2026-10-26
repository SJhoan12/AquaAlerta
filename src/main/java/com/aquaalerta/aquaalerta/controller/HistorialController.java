package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import com.aquaalerta.aquaalerta.service.ReporteLecturasExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.io.IOException;

@Controller
@RequestMapping("/historial")
public class HistorialController {

    private final LecturaNivelRepository lecturaNivelRepository;
    private final AlertaRepository alertaRepository;
    private final TanqueRepository tanqueRepository;
    private final ReporteLecturasExcelService reporteLecturasExcelService;

    public HistorialController(LecturaNivelRepository lecturaNivelRepository,
                               AlertaRepository alertaRepository,
                               TanqueRepository tanqueRepository,
                               ReporteLecturasExcelService reporteLecturasExcelService) {
        this.lecturaNivelRepository = lecturaNivelRepository;
        this.alertaRepository = alertaRepository;
        this.tanqueRepository = tanqueRepository;
        this.reporteLecturasExcelService = reporteLecturasExcelService;
    }

    @GetMapping
    public String historial(@RequestParam(required = false) String tanqueId,
                            @RequestParam(required = false) LocalDate desde,
                            @RequestParam(required = false) LocalDate hasta,
                            Model model) {
        validarRangoFechas(desde, hasta);
        List<LecturaNivel> lecturas = buscarLecturas(tanqueId, desde, hasta);
        model.addAttribute("lecturas", lecturas);
        model.addAttribute("tanqueId", tanqueId == null ? "" : tanqueId);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tanques", tanqueRepository.findAll());
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "historial";
    }

    @GetMapping("/exportar.xlsx")
    public ResponseEntity<byte[]> exportarExcel(@RequestParam(required = false) String tanqueId,
                                                @RequestParam(required = false) LocalDate desde,
                                                @RequestParam(required = false) LocalDate hasta)
            throws IOException {
        validarRangoFechas(desde, hasta);
        byte[] archivo = reporteLecturasExcelService.generar(
                buscarLecturas(tanqueId, desde, hasta));
        String nombreArchivo = "AquaAlerta_historial.xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreArchivo + "\"")
                .body(archivo);
    }

    private List<LecturaNivel> buscarLecturas(String tanqueId, LocalDate desde, LocalDate hasta) {
        List<LecturaNivel> lecturas = (tanqueId == null || tanqueId.isBlank())
                ? lecturaNivelRepository.findAll()
                : lecturaNivelRepository.findByTanqueIdOrderByTimestampDesc(tanqueId);
        return lecturas.stream()
                .filter(lectura -> desde == null || (lectura.getTimestamp() != null
                        && !lectura.getTimestamp().toLocalDate().isBefore(desde)))
                .filter(lectura -> hasta == null || (lectura.getTimestamp() != null
                        && !lectura.getTimestamp().toLocalDate().isAfter(hasta)))
                .sorted(Comparator.comparing(LecturaNivel::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private void validarRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final.");
        }
    }
}

package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import com.aquaalerta.aquaalerta.service.ReporteLecturasExcelService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistorialControllerTests {

    private final LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
    private final AlertaRepository alertaRepository = mock(AlertaRepository.class);
    private final TanqueRepository tanqueRepository = mock(TanqueRepository.class);
    private final HistorialController controller = new HistorialController(
            lecturaRepository,
            alertaRepository,
            tanqueRepository,
            new ReporteLecturasExcelService());

    @Test
    void exportsOnlyRowsMatchingTheSelectedTankAndDateRange() throws Exception {
        LecturaNivel beforeRange = lectura(LocalDateTime.of(2026, 10, 24, 23, 59));
        LecturaNivel matching = lectura(LocalDateTime.of(2026, 10, 25, 14, 30));
        LecturaNivel afterRange = lectura(LocalDateTime.of(2026, 10, 27, 0, 0));
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(afterRange, matching, beforeRange));

        var response = controller.exportarExcel(
                "tanque-01",
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 10, 26));

        assertEquals("attachment; filename=\"AquaAlerta_historial.xlsx\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(response.getHeaders().getContentType().toString()
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        verify(lecturaRepository).findByTanqueIdOrderByTimestampDesc("tanque-01");

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(response.getBody()))) {
            var sheet = workbook.getSheet("Historial");
            assertEquals(1, sheet.getLastRowNum());
            assertEquals("2026-10-25T14:30",
                    sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void rejectsDateRangesWhereStartIsAfterEnd() {
        assertThrows(ResponseStatusException.class,
                () -> controller.exportarExcel(
                        "tanque-01",
                        LocalDate.of(2026, 10, 27),
                        LocalDate.of(2026, 10, 25)));
    }

    private LecturaNivel lectura(LocalDateTime timestamp) {
        LecturaNivel lectura = new LecturaNivel();
        lectura.setTanqueId("tanque-01");
        lectura.setTimestamp(timestamp);
        return lectura;
    }
}

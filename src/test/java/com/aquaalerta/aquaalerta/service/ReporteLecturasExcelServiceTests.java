package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.LecturaNivel;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReporteLecturasExcelServiceTests {

    private final ReporteLecturasExcelService service = new ReporteLecturasExcelService();

    @Test
    void createsAnExcelWorkbookWithReadingDataAndStatus() throws Exception {
        LecturaNivel lectura = new LecturaNivel();
        lectura.setTanqueId("tanque-01");
        lectura.setSensorId("esp32-demo");
        lectura.setDistanciaCm(20.0);
        lectura.setDistanciasOriginalesCm(List.of(20.0, 21.0, 80.0));
        lectura.setTieneMuestrasAtipicas(true);
        lectura.setNivelCm(80.0);
        lectura.setVolumenLitros(628.3);
        lectura.setTimestamp(LocalDateTime.of(2026, 10, 26, 12, 0));

        byte[] bytes = service.generar(List.of(lectura));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Historial");
            assertNotNull(sheet);
            assertEquals("Fecha y hora", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("tanque-01", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("esp32-demo", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(20.0, sheet.getRow(1).getCell(3).getNumericCellValue());
            assertEquals("[20.0, 21.0, 80.0]",
                    sheet.getRow(1).getCell(4).getStringCellValue());
            assertEquals("Muestra atípica del sensor",
                    sheet.getRow(1).getCell(9).getStringCellValue());
        }
    }

    @Test
    void createsAWorkbookWithHeadersWhenThereAreNoReadings() throws Exception {
        byte[] bytes = service.generar(List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Historial");
            assertNotNull(sheet);
            assertEquals("Fecha y hora", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(0, sheet.getLastRowNum());
        }
    }
}

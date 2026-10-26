package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.LecturaNivel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ReporteLecturasExcelService {

    private static final String[] ENCABEZADOS = {
            "Fecha y hora",
            "Tanque ID",
            "Sensor ID",
            "Distancia representativa (cm)",
            "Muestras originales (cm)",
            "Muestras atípicas",
            "Nivel (cm)",
            "Volumen (L)",
            "Anomalia de consumo",
            "Estado"
    };

    public byte[] generar(List<LecturaNivel> lecturas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Historial");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int column = 0; column < ENCABEZADOS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(ENCABEZADOS[column]);
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < lecturas.size(); index++) {
                LecturaNivel lectura = lecturas.get(index);
                Row row = sheet.createRow(index + 1);
                setText(row, 0, lectura.getTimestamp() == null
                        ? "" : lectura.getTimestamp().toString());
                setText(row, 1, lectura.getTanqueId());
                setText(row, 2, lectura.getSensorId());
                row.createCell(3).setCellValue(lectura.getDistanciaCm());
                setText(row, 4, lectura.getDistanciasOriginalesCm().toString());
                setText(row, 5, lectura.isTieneMuestrasAtipicas() ? "Sí" : "No");
                row.createCell(6).setCellValue(lectura.getNivelCm());
                row.createCell(7).setCellValue(lectura.getVolumenLitros());
                setText(row, 8, lectura.isEsAnomalia() ? "Sí" : "No");
                setText(row, 9, obtenerEstado(lectura));
            }

            if (!lecturas.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, lecturas.size(), 0, ENCABEZADOS.length - 1));
            }
            for (int column = 0; column < ENCABEZADOS.length; column++) {
                sheet.autoSizeColumn(column);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void setText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private String obtenerEstado(LecturaNivel lectura) {
        if (lectura.isEsAnomalia()) {
            return "Anomalía de consumo";
        }
        if (lectura.isTieneMuestrasAtipicas()) {
            return "Muestra atípica del sensor";
        }
        return "Normal";
    }
}

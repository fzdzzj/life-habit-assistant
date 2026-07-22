package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.server.service.ReportExporter;
import com.lowagie.text.pdf.PdfReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExporterTest {
    @Test
    void exportsReadableExcelAndPdf() throws Exception {
        var r = new ReportDtos.ReportResponse("weekly", LocalDate.now(), LocalDate.now(), 1, 7.5, 4, 30, 1600, 0, 100, List.of(new AnalysisDtos.DailyTrend(LocalDate.now(), 7.5, 4, 30, 1600, 0, true)), List.of(), List.of(), List.of("保持习惯"));
        var e = new ReportExporter();
        byte[] xlsx = e.xlsx(r);
        byte[] pdf = e.pdf(r);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals("Summary", workbook.getSheetAt(0).getSheetName());
            assertTrue(workbook.getSheet("Daily trends").getLastRowNum() >= 1);
            assertTrue(workbook.getSheet("Risks and advice").getLastRowNum() >= 1);
        }
        try (PdfReader reader = new PdfReader(pdf)) {
            assertTrue(reader.getNumberOfPages() >= 1);
        }
    }
}

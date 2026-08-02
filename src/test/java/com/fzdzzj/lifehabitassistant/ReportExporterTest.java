package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AdviceSource;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.server.service.ReportExporter;
import com.lowagie.text.pdf.PdfReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExporterTest {
    @Test
    void exportsDetailedTrendColumnsInReadableExcelAndPdf() throws Exception {
        var trend = new AnalysisDtos.DailyTrend(LocalDate.now(), 7.5, 4, 30, 1600, 0, true,
                7.0, 0.5, 60, Map.of(ExerciseType.RUN, 30));
        var report = new ReportDtos.ReportResponse("weekly", LocalDate.now(), LocalDate.now(), 1, 7.5, 4,
                30, 1600, 0, 100, List.of(trend), List.of(), List.of(), List.of("保持习惯"), null);
        var exporter = new ReportExporter();
        byte[] xlsx = exporter.xlsx(report);
        byte[] pdf = exporter.pdf(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals("Summary", workbook.getSheetAt(0).getSheetName());
            var trends = workbook.getSheet("Daily trends");
            assertEquals("Night sleep (h)", trends.getRow(0).getCell(2).getStringCellValue());
            assertEquals("RUN: 30 min", trends.getRow(1).getCell(7).getStringCellValue());
            assertTrue(workbook.getSheet("Risks and advice").getLastRowNum() >= 1);
            assertEquals("AI advice", workbook.getSheet("AI advice").getSheetName());
        }
        try (PdfReader reader = new PdfReader(pdf)) {
            assertTrue(reader.getNumberOfPages() >= 1);
        }
    }

    @Test
    void exportsShouldIncludeSavedAiAdviceContent() throws Exception {
        var trend = new AnalysisDtos.DailyTrend(LocalDate.now(), 7.5, 4, 30, 1600, 0, true,
                7.0, 0.5, 60, Map.of(ExerciseType.RUN, 30));
        var content = new AiAdviceDtos.AiAdviceContent("整体稳定", "睡眠略不足",
                List.of("固定就寝时间"), "每天记录", "继续保持", "仅供健康参考");
        var snapshot = new AiAdviceDtos.AdviceSnapshot(1L, AdviceSource.AI, content,
                LocalDate.now().atStartOfDay());
        var report = new ReportDtos.ReportResponse("weekly", LocalDate.now(), LocalDate.now(), 1, 7.5, 4,
                30, 1600, 0, 100, List.of(trend), List.of(), List.of(), List.of(), snapshot);
        var exporter = new ReportExporter();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exporter.xlsx(report)))) {
            var sheet = workbook.getSheet("AI advice");
            assertEquals("Period summary", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("整体稳定", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("Recommendation", sheet.getRow(5).getCell(0).getStringCellValue());
            assertEquals("固定就寝时间", sheet.getRow(5).getCell(1).getStringCellValue());
        }
        try (PdfReader reader = new PdfReader(exporter.pdf(report))) {
            assertTrue(reader.getNumberOfPages() >= 1);
        }
    }
}

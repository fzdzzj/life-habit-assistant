package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ReportExporter {
    public byte[] xlsx(ReportDtos.ReportResponse report) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = book.createCellStyle();
            Font font = book.createFont();
            font.setBold(true);
            header.setFont(font);
            Sheet summary = book.createSheet("Summary");
            row(summary, 0, header, "Type", "Period", "Records", "Avg sleep (h)", "Avg diet", "Exercise (min)", "Avg hydration (ml)", "Risk drinks (ml)", "Achievement");
            row(summary, 1, null, report.type(), report.periodStart() + " to " + report.periodEnd(), report.recordCount(), report.averageSleepHours(), report.averageDietScore(), report.totalExerciseMinutes(), report.averageHydrationMl(), report.totalRiskDrinkVolumeMl(), report.achievementRate() + "%");
            Sheet trends = book.createSheet("Daily trends");
            row(trends, 0, header, "Date", "Sleep (h)", "Night sleep (h)", "Nap sleep (h)", "Diet score",
                    "Exercise (min)", "Moderate equivalent (min)", "Exercise types", "Hydration (ml)",
                    "Risk drinks (ml)", "Achieved");
            int i = 1;
            for (var d : report.dailyTrends())
                row(trends, i++, null, d.date(), d.sleepHours(), d.nightSleepHours(), d.napSleepHours(), d.dietScore(),
                        d.exerciseMinutes(), d.moderateEquivalentExerciseMinutes(), formatExerciseTypes(d), d.hydrationMl(),
                        d.riskDrinkVolumeMl(), d.achieved() ? "Yes" : "No");
            Sheet weekly = book.createSheet("Weekly summaries");
            row(weekly, 0, header, "Week start", "Avg sleep (h)", "Exercise (min)", "Avg hydration (ml)", "Risk drinks (ml)");
            i = 1;
            for (var summaryItem : report.weeklySummaries())
                row(weekly, i++, null, summaryItem.weekStart(), summaryItem.averageSleepHours(), summaryItem.exerciseMinutes(), summaryItem.averageHydrationMl(), summaryItem.riskDrinkVolumeMl());
            Sheet advice = book.createSheet("Risks and advice");
            row(advice, 0, header, "Category", "Content");
            i = 1;
            for (String risk : report.risks()) row(advice, i++, null, "Risk", risk);
            for (String suggestion : report.suggestions()) row(advice, i++, null, "Suggestion", suggestion);
            for (Sheet s : java.util.List.of(summary, trends, weekly, advice))
                for (int c = 0; c < s.getRow(0).getLastCellNum(); c++) s.autoSizeColumn(c);
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("无法生成 Excel 报告", e);
        }
    }

    public byte[] pdf(ReportDtos.ReportResponse r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            com.lowagie.text.Font title = new com.lowagie.text.Font(baseFont, 18, com.lowagie.text.Font.BOLD), normal = new com.lowagie.text.Font(baseFont, 10);
            doc.add(new Paragraph(("Life Habit " + r.type() + " report").toUpperCase(), title));
            doc.add(new Paragraph("Period: " + r.periodStart() + " to " + r.periodEnd(), normal));
            doc.add(new Paragraph("Records: " + r.recordCount() + " | Avg sleep: " + r.averageSleepHours() + " h | Avg diet: " + r.averageDietScore() + " | Exercise: " + r.totalExerciseMinutes() + " min | Avg hydration: " + r.averageHydrationMl() + " ml | Risk drinks: " + r.totalRiskDrinkVolumeMl() + " ml | Achievement: " + r.achievementRate() + "%", normal));
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Daily trends", title));
            PdfPTable table = new PdfPTable(11);
            for (String h : new String[]{"Date", "Sleep", "Night", "Nap", "Diet", "Exercise", "Equivalent", "Types", "Hydration", "Risk drinks", "Achieved"})
                table.addCell(new Phrase(h, normal));
            for (var d : r.dailyTrends()) {
                table.addCell(d.date().toString());
                table.addCell(d.sleepHours() + " h");
                table.addCell(d.nightSleepHours() + " h");
                table.addCell(d.napSleepHours() + " h");
                table.addCell(String.valueOf(d.dietScore()));
                table.addCell(d.exerciseMinutes() + " min");
                table.addCell(d.moderateEquivalentExerciseMinutes() + " min");
                table.addCell(formatExerciseTypes(d));
                table.addCell(d.hydrationMl() + " ml");
                table.addCell(d.riskDrinkVolumeMl() + " ml");
                table.addCell(d.achieved() ? "Yes" : "No");
            }
            doc.add(table);
            if (!r.weeklySummaries().isEmpty()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("Weekly summaries", title));
                PdfPTable weeklyTable = new PdfPTable(5);
                for (String h : new String[]{"Week start", "Avg sleep", "Exercise", "Avg hydration", "Risk drinks"})
                    weeklyTable.addCell(new Phrase(h, normal));
                for (var summaryItem : r.weeklySummaries()) {
                    weeklyTable.addCell(new Phrase(summaryItem.weekStart().toString(), normal));
                    weeklyTable.addCell(new Phrase(summaryItem.averageSleepHours() + " h", normal));
                    weeklyTable.addCell(new Phrase(summaryItem.exerciseMinutes() + " min", normal));
                    weeklyTable.addCell(new Phrase(summaryItem.averageHydrationMl() + " ml", normal));
                    weeklyTable.addCell(new Phrase(summaryItem.riskDrinkVolumeMl() + " ml", normal));
                }
                doc.add(weeklyTable);
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Risks", title));
            for (String v : r.risks()) doc.add(new Paragraph("- " + v, normal));
            doc.add(new Paragraph("Suggestions", title));
            for (String v : r.suggestions()) doc.add(new Paragraph("- " + v, normal));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("无法生成 PDF 报告", e);
        }
    }

    private void row(Sheet sheet, int index, CellStyle style, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            if (style != null) cell.setCellStyle(style);
            Object v = values[i];
            if (v instanceof Number n) cell.setCellValue(n.doubleValue());
            else cell.setCellValue(String.valueOf(v));
        }
    }

    private String formatExerciseTypes(com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos.DailyTrend dailyTrend) {
        return dailyTrend.exerciseMinutesByType().entrySet().stream()
                .map(entry -> entry.getKey().name() + ": " + entry.getValue() + " min")
                .collect(java.util.stream.Collectors.joining("; "));
    }
}

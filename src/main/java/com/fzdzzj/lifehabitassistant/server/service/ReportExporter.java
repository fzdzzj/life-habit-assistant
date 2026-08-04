package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
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
                    "Risk drinks (ml)", "Achieved", "目标 vs 实际");
            int goalColumn = 11;
            CellStyle wrap = book.createCellStyle();
            wrap.setWrapText(true);
            int i = 1;
            for (var d : report.dailyTrends()) {
                row(trends, i++, null, d.date(), d.sleepHours(), d.nightSleepHours(), d.napSleepHours(), d.dietScore(),
                        d.exerciseMinutes(), d.moderateEquivalentExerciseMinutes(), formatExerciseTypes(d), d.hydrationMl(),
                        d.riskDrinkVolumeMl(), d.achieved() ? "Yes" : "No");
                Cell goalCell = trends.getRow(i - 1).createCell(goalColumn);
                goalCell.setCellValue(String.join("\n", goalVsActual(d, report.goals())));
                goalCell.setCellStyle(wrap);
                trends.getRow(i - 1).setHeight((short) 800);
            }
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
            Sheet aiAdvice = book.createSheet("AI advice");
            row(aiAdvice, 0, header, "Category", "Content");
            i = 1;
            if (report.aiAdvice() == null) {
                row(aiAdvice, i, null, "Status", "该周期没有已保存的 AI 解读；请先显式调用 AI 解读接口");
            } else {
                var content = report.aiAdvice().content();
                row(aiAdvice, i++, null, "Source", report.aiAdvice().source().name());
                row(aiAdvice, i++, null, "Created at", report.aiAdvice().createdAt());
                row(aiAdvice, i++, null, "Period summary", content.periodSummary());
                row(aiAdvice, i++, null, "Risk explanation", content.riskExplanation());
                for (String recommendation : content.recommendations()) {
                    row(aiAdvice, i++, null, "Recommendation", recommendation);
                }
                row(aiAdvice, i++, null, "Next period plan", content.nextPeriodPlan());
                row(aiAdvice, i++, null, "Encouragement", content.encouragement());
                row(aiAdvice, i++, null, "Disclaimer", content.disclaimer());
            }
            for (Sheet s : java.util.List.of(summary, trends, weekly, advice, aiAdvice))
                for (int c = 0; c < s.getRow(0).getLastCellNum(); c++)
                    if (!(s == trends && c == goalColumn)) s.autoSizeColumn(c);
            trends.setColumnWidth(goalColumn, 22 * 256);
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
            PdfPTable table = new PdfPTable(12);
            for (String h : new String[]{"Date", "Sleep", "Night", "Nap", "Diet", "Exercise", "Equivalent", "Types", "Hydration", "Risk drinks", "Achieved", "目标 vs 实际"})
                table.addCell(new Phrase(h, normal));
            com.lowagie.text.Font small = new com.lowagie.text.Font(baseFont, 8);
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
                PdfPCell goalCell = new PdfPCell();
                goalCell.setPadding(2);
                for (String line : goalVsActual(d, r.goals())) {
                    goalCell.addElement(new Paragraph(line, small));
                }
                table.addCell(goalCell);
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
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("AI advice", title));
            if (r.aiAdvice() == null) {
                doc.add(new Paragraph("该周期没有已保存的 AI 解读；请先显式调用 AI 解读接口。", normal));
            } else {
                var content = r.aiAdvice().content();
                doc.add(new Paragraph("Source: " + r.aiAdvice().source().name() + " | Created at: " + r.aiAdvice().createdAt(), normal));
                doc.add(new Paragraph("Period summary", title));
                doc.add(new Paragraph(content.periodSummary(), normal));
                doc.add(new Paragraph("Risk explanation", title));
                doc.add(new Paragraph(content.riskExplanation(), normal));
                doc.add(new Paragraph("Recommendations", title));
                for (String v : content.recommendations()) doc.add(new Paragraph("- " + v, normal));
                doc.add(new Paragraph("Next period plan", title));
                doc.add(new Paragraph(content.nextPeriodPlan(), normal));
                doc.add(new Paragraph("Encouragement", title));
                doc.add(new Paragraph(content.encouragement(), normal));
                doc.add(new Paragraph("Disclaimer", title));
                doc.add(new Paragraph(content.disclaimer(), normal));
            }
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

    private java.util.List<String> goalVsActual(AnalysisDtos.DailyTrend dailyTrend, DailyGoals goals) {
        return java.util.List.of(
                String.format("睡眠 %.1fh/%s", dailyTrend.sleepHours(), sleepGoalRange(goals)),
                String.format("运动 %dmin/%dmin", dailyTrend.exerciseMinutes(), goals.minimumExerciseMinutes()),
                String.format("补水 %dml/%dml", dailyTrend.hydrationMl(), goals.minimumHydrationMl()),
                String.format("饮食 %d分/%d分", dailyTrend.dietScore(), goals.minimumDietScore()));
    }

    private String sleepGoalRange(DailyGoals goals) {
        String min = formatHours(goals.minimumSleepMinutes());
        String max = formatHours(goals.maximumSleepMinutes());
        return min.equals(max) ? min + "h" : min + "~" + max + "h";
    }

    private String formatHours(int minutes) {
        return minutes % 60 == 0 ? String.valueOf(minutes / 60) : String.format("%.1f", minutes / 60d);
    }
}

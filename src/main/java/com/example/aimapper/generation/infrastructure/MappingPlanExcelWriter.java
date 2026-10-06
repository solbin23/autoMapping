package com.example.aimapper.generation.infrastructure;

import com.example.aimapper.generation.domain.MappingDecision;
import com.example.aimapper.generation.domain.MappingProject;
import com.example.aimapper.matching.domain.FieldMatch;
import com.example.aimapper.schema.domain.SchemaField;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 규칙 추천과 사용자 최종 결정을 비교할 수 있는 XLSX 매핑 계획서를 생성한다. */
@Component
public class MappingPlanExcelWriter {

    private static final List<String> HEADERS = List.of(
            "AS-IS 클래스",
            "AS-IS 경로",
            "AS-IS 타입",
            "규칙 추천 클래스",
            "규칙 추천 경로",
            "규칙 점수",
            "최종 상태",
            "TO-BE 클래스",
            "TO-BE 경로",
            "TO-BE 타입",
            "변환 타입",
            "Mapper 포함");

    /** 매핑 계획과 프로젝트 메타데이터 시트가 포함된 엑셀 파일을 메모리에서 만든다. */
    public byte[] write(MappingProject project) {
        try (var workbook = new XSSFWorkbook();
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("매핑 계획");
            sheet.createFreezePane(0, 1);

            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            var headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                var cell = header.createCell(column);
                cell.setCellValue(HEADERS.get(column));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (MappingDecision decision : project.decisions()) {
                SchemaField source = find(project.recommendations().asIs(),
                        decision.source().className(), decision.source().path());
                FieldMatch recommendation = project.recommendations().matches().stream()
                        .filter(match -> match.source().className().equals(decision.source().className()))
                        .filter(match -> match.source().field().path().equals(decision.source().path()))
                        .findFirst()
                        .orElse(null);
                var best = recommendation == null || recommendation.candidates().isEmpty()
                        ? null : recommendation.candidates().getFirst();
                SchemaField target = decision.target() == null ? null
                        : find(project.recommendations().toBe(),
                        decision.target().className(), decision.target().path());

                var row = sheet.createRow(rowIndex++);
                string(row, 0, decision.source().className());
                string(row, 1, decision.source().path());
                string(row, 2, source.javaType());
                string(row, 3, best == null ? null : best.target().className());
                string(row, 4, best == null ? null : best.target().field().path());
                if (best != null) row.createCell(5).setCellValue(best.score());
                else string(row, 5, null);
                string(row, 6, decision.status().name());
                string(row, 7, decision.target() == null ? null : decision.target().className());
                string(row, 8, decision.target() == null ? null : decision.target().path());
                string(row, 9, target == null ? null : target.javaType());
                string(row, 10, decision.conversionType() == null ? null : decision.conversionType().name());
                string(row, 11, decision.status() == MappingDecision.Status.APPROVED ? "Y" : "N");
            }

            sheet.setAutoFilter(new CellRangeAddress(
                    0, Math.max(0, project.decisions().size()), 0, HEADERS.size() - 1));
            for (int column = 0; column < HEADERS.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 700, 20_000));
            }

            var metadata = workbook.createSheet("내보내기 정보");
            metadata.createRow(0).createCell(0).setCellValue("프로젝트 ID");
            metadata.getRow(0).createCell(1).setCellValue(project.id().toString());
            metadata.createRow(1).createCell(0).setCellValue("Revision");
            metadata.getRow(1).createCell(1).setCellValue(project.revision());
            metadata.createRow(2).createCell(0).setCellValue("승인된 매핑 수");
            metadata.getRow(2).createCell(1).setCellValue(project.decisions().stream()
                    .filter(decision -> decision.status() == MappingDecision.Status.APPROVED).count());
            metadata.autoSizeColumn(0);
            metadata.autoSizeColumn(1);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create mapping plan Excel", exception);
        }
    }

    private SchemaField find(SchemaSnapshot snapshot, String className, String path) {
        return snapshot.classes().stream()
                .filter(schemaClass -> schemaClass.qualifiedName().equals(className))
                .flatMap(schemaClass -> schemaClass.fields().stream())
                .filter(field -> field.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown schema field: " + className + ":" + path));
    }

    private void string(org.apache.poi.ss.usermodel.Row row, int column, Object value) {
        row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
    }
}

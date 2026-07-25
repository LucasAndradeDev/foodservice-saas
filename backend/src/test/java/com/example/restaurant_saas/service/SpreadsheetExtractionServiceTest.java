package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.MenuImportProcessingException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpreadsheetExtractionServiceTest {

    private final SpreadsheetExtractionService service = new SpreadsheetExtractionService();

    @Test
    void extractFlattenedText_withMultipleSheets_shouldFlattenAllOfThem() throws IOException {
        MockMultipartFile file = xlsxWithSheets(
                new String[][]{{"Bebidas", ""}, {"Coca-Cola", "5.90"}},
                new String[][]{{"Lanches", ""}, {"Cheeseburger", "25.90"}}
        );

        String text = service.extractFlattenedText(file);

        assertThat(text).contains("### Aba: Sheet 0", "### Aba: Sheet 1", "Coca-Cola", "Cheeseburger");
    }

    @Test
    void extractFlattenedText_withEmptyWorkbook_shouldThrowProcessingException() throws IOException {
        MockMultipartFile file = xlsxWithSheets(new String[][]{});

        assertThatThrownBy(() -> service.extractFlattenedText(file))
                .isInstanceOf(MenuImportProcessingException.class);
    }

    @Test
    void extractFlattenedText_withCorruptFile_shouldThrowProcessingException() {
        MockMultipartFile file = new MockMultipartFile("file", "menu.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "not a real xlsx".getBytes());

        assertThatThrownBy(() -> service.extractFlattenedText(file))
                .isInstanceOf(MenuImportProcessingException.class);
    }

    @Test
    void extractFlattenedText_withNonXlsxFilename_shouldThrowIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "menu.csv", "text/csv", "a,b,c".getBytes());

        assertThatThrownBy(() -> service.extractFlattenedText(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractFlattenedText_withMissingFile_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> service.extractFlattenedText(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockMultipartFile xlsxWithSheets(String[][]... sheets) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (int s = 0; s < sheets.length; s++) {
                Sheet sheet = workbook.createSheet("Sheet " + s);
                String[][] rows = sheets[s];
                for (int r = 0; r < rows.length; r++) {
                    Row row = sheet.createRow(r);
                    for (int c = 0; c < rows[r].length; c++) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "menu.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}

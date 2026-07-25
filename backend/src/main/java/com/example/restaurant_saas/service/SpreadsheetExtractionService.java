package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.MenuImportProcessingException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpreadsheetExtractionService {

    private static final int MAX_ROWS_PER_SHEET = 2000;
    private static final int MAX_TOTAL_CHARS = 60_000;

    public String extractFlattenedText(MultipartFile file) {
        validate(file);

        StringBuilder text = new StringBuilder();
        boolean anyDataRow = false;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter formatter = new DataFormatter();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                text.append("### Aba: ").append(sheet.getSheetName()).append('\n');

                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ > MAX_ROWS_PER_SHEET || text.length() > MAX_TOTAL_CHARS) {
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            cells.add(value);
                        }
                    }
                    if (!cells.isEmpty()) {
                        anyDataRow = true;
                        text.append(String.join(" | ", cells)).append('\n');
                    }
                }
            }
        } catch (IOException | EncryptedDocumentException e) {
            throw new MenuImportProcessingException(
                    "Não foi possível ler a planilha. Verifique se o arquivo é um .xlsx válido, não está corrompido e não está protegido por senha.",
                    e);
        }

        if (!anyDataRow) {
            throw new MenuImportProcessingException("A planilha está vazia ou não contém dados legíveis.");
        }

        String result = text.toString().trim();
        return result.length() > MAX_TOTAL_CHARS ? result.substring(0, MAX_TOTAL_CHARS) : result;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Apenas arquivos .xlsx são suportados.");
        }
    }
}

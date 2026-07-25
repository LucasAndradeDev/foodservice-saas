package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.MenuImportProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Only this class knows about Gemini's wire format. Everything else in the
 * import feature deals with {@link GeminiExtractionResult}.
 */
@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "categories", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "name", Map.of("type", "STRING"),
                                            "products", Map.of(
                                                    "type", "ARRAY",
                                                    "items", Map.of(
                                                            "type", "OBJECT",
                                                            "properties", Map.of(
                                                                    "name", Map.of("type", "STRING"),
                                                                    "description", Map.of("type", "STRING"),
                                                                    "price", Map.of("type", "STRING")
                                                            ),
                                                            "required", List.of("name")
                                                    )
                                            )
                                    ),
                                    "required", List.of("name", "products")
                            )
                    )
            ),
            "required", List.of("categories")
    );

    private static final String PROMPT_TEMPLATE = """
            Você é um assistente que estrutura dados de cardápios de restaurante extraídos de uma planilha Excel.
            Abaixo está um dump bruto de todas as células de todas as abas da planilha, linha por linha, com as \
            células de cada linha separadas por " | ". O layout é desconhecido e pode estar desorganizado, com \
            cabeçalhos, totais ou linhas em branco misturados aos dados.

            Identifique categorias de cardápio e, para cada uma, os produtos pertencentes a ela (nome, preço e \
            descrição, quando houver). Ignore cabeçalhos, totais e linhas que não sejam produtos. Se não houver \
            uma categoria explícita pra um produto, agrupe sob "Outros". Extraia o preço como texto numérico \
            simples (ex: "12.90" ou "12,90"), exatamente como aparece na planilha - nunca invente ou estime um \
            valor. Se não houver preço identificável pra um produto, retorne null nesse campo. Não invente \
            produtos que não estão na planilha.

            Regras de categoria:
            - Se um produto se encaixar em uma das categorias JÁ EXISTENTES listadas abaixo, use exatamente o \
            mesmo nome dela (mesma grafia) em vez de criar uma categoria nova, mesmo que a planilha sugira um \
            nome diferente ou mais genérico para essa categoria.
            - Se mais de uma categoria já existente for uma opção plausível para o mesmo produto (por exemplo \
            "Hambúrguer" e "Lanches" existem e o produto é um hambúrguer), escolha a mais específica para esse \
            produto, não a mais genérica.
            - Se, entre as categorias que você for criar, duas tiverem nomes muito parecidos (variação de \
            singular/plural, acentuação ou grafia - por exemplo "Bebida" e "Bebidas"), use apenas um nome para \
            agrupar todos os produtos correspondentes; não crie as duas.

            Categorias já existentes neste restaurante:
            %s

            Dados da planilha:
            <<<
            %s
            >>>

            A planilha acima tem %d aba(s), cada uma marcada com "### Aba: <nome>". Processe TODAS elas, não \
            pare depois da primeira. Antes de responder, releia sua lista de categorias comparando com cada \
            aba do dump acima e confirme que nenhum produto de nenhuma aba ficou de fora.
            """;

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.api-key}")
    private String apiKey;

    public GeminiExtractionResult extractMenu(String flattenedSpreadsheetText, List<String> existingCategoryNames) {
        String existingCategoriesText = existingCategoryNames == null || existingCategoryNames.isEmpty()
                ? "(nenhuma - este é o primeiro cadastro de cardápio deste restaurante)"
                : existingCategoryNames.stream().map(name -> "- " + name).collect(Collectors.joining("\n"));

        int sheetCount = Math.max(1, countOccurrences(flattenedSpreadsheetText, "### Aba: "));
        String prompt = PROMPT_TEMPLATE.formatted(existingCategoriesText, flattenedSpreadsheetText, sheetCount);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA
                )
        );

        String rawResponse;
        try {
            rawResponse = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new MenuImportProcessingException("Falha ao consultar o serviço de IA. Tente novamente em instantes.", e);
        } catch (ResourceAccessException e) {
            throw new MenuImportProcessingException("A extração demorou demais para responder. Tente novamente.", e);
        }

        return parseCandidateJson(rawResponse);
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    private GeminiExtractionResult parseCandidateJson(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste a planilha.");
            }

            String finishReason = candidates.get(0).path("finishReason").asText("");
            if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste a planilha.");
            }

            String candidateText = candidates.get(0).path("content").path("parts").path(0).path("text").asText(null);
            if (candidateText == null || candidateText.isBlank()) {
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste a planilha.");
            }

            return objectMapper.readValue(candidateText, GeminiExtractionResult.class);
        } catch (MenuImportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste a planilha.", e);
        }
    }
}

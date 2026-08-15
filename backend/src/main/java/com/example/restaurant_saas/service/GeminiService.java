package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.MenuImportProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
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

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

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
            descrição, quando houver). Ignore cabeçalhos, totais e linhas que não sejam produtos. Extraia o \
            preço como texto numérico simples (ex: "12.90" ou "12,90"), exatamente como aparece na planilha - \
            nunca invente ou estime um valor. Se não houver preço identificável pra um produto, retorne null \
            nesse campo. Não invente produtos que não estão na planilha.

            Regras de categoria:
            - Se a planilha não indicar explicitamente a categoria de um produto, infira uma categoria \
            específica e descritiva a partir do próprio tipo do produto (ex: "Drinks", "Sobremesas", \
            "Entradas", "Massas", "Bebidas Quentes") - nunca use um nome genérico como "Outros" ou "Diversos" \
            só porque a categoria não estava escrita explicitamente. Reserve "Outros" apenas para o último \
            recurso, quando o produto realmente não se encaixa em nenhum agrupamento temático razoável junto \
            aos demais produtos da planilha.
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

    private static final String DOCUMENT_PROMPT_TEMPLATE = """
            Você é um assistente que estrutura dados de cardápios de restaurante a partir de fotos ou páginas \
            de PDF anexadas a esta mensagem.

            As imagens/páginas anexadas são o cardápio de um restaurante - podem ser fotos tiradas com celular \
            de um cardápio físico (impresso ou em quadro) ou páginas de um PDF. Podem estar em ângulo, com \
            iluminação irregular, fontes decorativas, colunas ou tabelas. Se houver mais de uma imagem/página, \
            elas juntas formam o mesmo cardápio - combine tudo em um único resultado, sem repetir um produto \
            que apareça em mais de uma imagem.

            Identifique categorias de cardápio e, para cada uma, os produtos pertencentes a ela (nome, preço e \
            descrição, quando houver). Ignore elementos decorativos, logotipos e textos de propaganda que não \
            sejam produto. Extraia o preço como texto numérico simples (ex: "12.90" ou "12,90"), exatamente \
            como aparece na imagem - nunca invente ou estime um valor. Se não houver preço identificável pra \
            um produto, retorne null nesse campo. Não invente produtos que não estão nas imagens.

            Regras de categoria:
            - Se a imagem/página não indicar explicitamente a categoria de um produto, infira uma categoria \
            específica e descritiva a partir do próprio tipo do produto (ex: "Drinks", "Sobremesas", \
            "Entradas", "Massas", "Bebidas Quentes") - nunca use um nome genérico como "Outros" ou "Diversos" \
            só porque a categoria não estava escrita explicitamente. Reserve "Outros" apenas para o último \
            recurso, quando o produto realmente não se encaixa em nenhum agrupamento temático razoável junto \
            aos demais produtos do cardápio.
            - Se um produto se encaixar em uma das categorias JÁ EXISTENTES listadas abaixo, use exatamente o \
            mesmo nome dela (mesma grafia) em vez de criar uma categoria nova, mesmo que o cardápio sugira um \
            nome diferente ou mais genérico para essa categoria.
            - Se mais de uma categoria já existente for uma opção plausível para o mesmo produto (por exemplo \
            "Hambúrguer" e "Lanches" existem e o produto é um hambúrguer), escolha a mais específica para esse \
            produto, não a mais genérica.
            - Se, entre as categorias que você for criar, duas tiverem nomes muito parecidos (variação de \
            singular/plural, acentuação ou grafia - por exemplo "Bebida" e "Bebidas"), use apenas um nome para \
            agrupar todos os produtos correspondentes; não crie as duas.

            Categorias já existentes neste restaurante:
            %s

            Esta importação tem %d imagem(ns)/página(ns) anexada(s). Processe TODAS elas antes de responder, \
            não pare depois da primeira - releia sua lista de produtos comparando com cada imagem/página e \
            confirme que nada ficou de fora.
            """;

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.models}")
    private String[] models;

    @Value("${gemini.api-key}")
    private String apiKey;

    public GeminiExtractionResult extractMenu(String flattenedSpreadsheetText, List<String> existingCategoryNames) {
        String existingCategoriesText = formatExistingCategories(existingCategoryNames);

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

        return callGemini(requestBody);
    }

    public GeminiExtractionResult extractMenuFromDocuments(List<GeminiDocument> documents, List<String> existingCategoryNames) {
        String existingCategoriesText = formatExistingCategories(existingCategoryNames);
        String prompt = DOCUMENT_PROMPT_TEMPLATE.formatted(existingCategoriesText, documents.size());

        List<Map<String, Object>> parts = new ArrayList<>();
        for (GeminiDocument document : documents) {
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", document.mimeType(),
                    "data", Base64.getEncoder().encodeToString(document.data())
            )));
        }
        parts.add(Map.of("text", prompt));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA
                )
        );

        return callGemini(requestBody);
    }

    private String formatExistingCategories(List<String> existingCategoryNames) {
        return existingCategoryNames == null || existingCategoryNames.isEmpty()
                ? "(nenhuma - este é o primeiro cadastro de cardápio deste restaurante)"
                : existingCategoryNames.stream().map(name -> "- " + name).collect(Collectors.joining("\n"));
    }

    private GeminiExtractionResult callGemini(Map<String, Object> requestBody) {
        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            boolean isLastModel = i == models.length - 1;
            try {
                String rawResponse = geminiRestClient.post()
                        .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                return parseCandidateJson(rawResponse);
            } catch (RestClientResponseException e) {
                log.warn("Gemini model {} failed with status {}: {}", model, e.getStatusCode(), e.getResponseBodyAsString());
                int status = e.getStatusCode().value();
                // 429/503 are per-model quota/overload - 404 covers a model name that's been
                // retired or isn't available to this account. All three mean "try the next
                // model in the chain", not "give up".
                boolean shouldTryNextModel = (status == 429 || status == 503 || status == 404) && !isLastModel;
                if (shouldTryNextModel) {
                    continue;
                }
                if (status == 429) {
                    throw new MenuImportProcessingException("Cota diária do serviço de IA esgotada. Tente novamente amanhã.", e);
                }
                if (status == 503) {
                    throw new MenuImportProcessingException("O serviço de IA está sobrecarregado no momento. Tente novamente em instantes.", e);
                }
                throw new MenuImportProcessingException("Falha ao consultar o serviço de IA. Tente novamente em instantes.", e);
            } catch (ResourceAccessException e) {
                log.warn("Gemini model {} timed out or failed to connect", model, e);
                throw new MenuImportProcessingException("A extração demorou demais para responder. Tente novamente.", e);
            }
        }
        throw new IllegalStateException("gemini.models must not be empty");
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
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste o arquivo enviado.");
            }

            String finishReason = candidates.get(0).path("finishReason").asText("");
            if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste o arquivo enviado.");
            }

            String candidateText = candidates.get(0).path("content").path("parts").path(0).path("text").asText(null);
            if (candidateText == null || candidateText.isBlank()) {
                throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste o arquivo enviado.");
            }

            return objectMapper.readValue(candidateText, GeminiExtractionResult.class);
        } catch (MenuImportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new MenuImportProcessingException("Não foi possível interpretar a resposta da IA. Tente novamente ou ajuste o arquivo enviado.", e);
        }
    }
}

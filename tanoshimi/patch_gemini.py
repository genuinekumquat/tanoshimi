import re

file_path = r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\util\GeminiChatClient.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

imports = """import org.springframework.web.reactive.function.client.WebClient;
import net.datasa.tanoshimi.service.VivianQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;"""

if "VivianQueryService" not in content:
    content = content.replace("import org.springframework.web.reactive.function.client.WebClient;", imports)

autowired = """private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VivianQueryService queryService;"""
if "@Autowired" not in content:
    content = content.replace("private final ObjectMapper objectMapper = new ObjectMapper();", autowired)

tools = """
            ArrayNode tools = objectMapper.createArrayNode();
            
            ObjectNode functionDeclarationsNode = objectMapper.createObjectNode();
            ArrayNode functionDeclarations = objectMapper.createArrayNode();
            
            ObjectNode getSiteStatsFunc = objectMapper.createObjectNode();
            getSiteStatsFunc.put("name", "getSiteStats");
            getSiteStatsFunc.put("description", "Returns total registered users, tours, and active trip schedules. Do not expose personal data.");
            functionDeclarations.add(getSiteStatsFunc);
            
            ObjectNode searchToursFunc = objectMapper.createObjectNode();
            searchToursFunc.put("name", "searchTours");
            searchToursFunc.put("description", "Search for available tour locations and packages by title keyword");
            ObjectNode searchToursParams = objectMapper.createObjectNode();
            searchToursParams.put("type", "OBJECT");
            ObjectNode searchToursProps = objectMapper.createObjectNode();
            ObjectNode keywordProp = objectMapper.createObjectNode();
            keywordProp.put("type", "STRING");
            keywordProp.put("description", "The search keyword");
            searchToursProps.set("keyword", keywordProp);
            searchToursParams.set("properties", searchToursProps);
            ArrayNode requiredArgs = objectMapper.createArrayNode();
            requiredArgs.add("keyword");
            searchToursParams.set("required", requiredArgs);
            searchToursFunc.set("parameters", searchToursParams);
            functionDeclarations.add(searchToursFunc);
            
            functionDeclarationsNode.set("function_declarations", functionDeclarations);
            tools.add(functionDeclarationsNode);

            ObjectNode googleSearchTool = objectMapper.createObjectNode();
            googleSearchTool.set("google_search", objectMapper.createObjectNode());
            tools.add(googleSearchTool);
            body.set("tools", tools);
"""
content = re.sub(
    r'ArrayNode tools = objectMapper\.createArrayNode\(\);.*?body\.set\("tools", tools\);',
    tools.strip(),
    content,
    flags=re.DOTALL
)

loop_logic = """
            for (int i = 0; i < 5; i++) {
                body.set("contents", contents);

                JsonNode response = webClient.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
                        
                if (response == null) break;

                JsonNode candidate = response.path("candidates").path(0);
                JsonNode candidateContent = candidate.path("content");
                JsonNode parts = candidateContent.path("parts");

                if (parts != null && parts.isArray()) {
                    boolean hasFunctionCall = false;
                    for (JsonNode part : parts) {
                        if (part.has("functionCall")) {
                            hasFunctionCall = true;
                            JsonNode funcCall = part.get("functionCall");
                            String funcName = funcCall.get("name").asText();
                            JsonNode args = funcCall.get("args");

                            Map<String, Object> result = java.util.Collections.emptyMap();
                            try {
                                if ("getSiteStats".equals(funcName)) {
                                    result = queryService.getSiteStats();
                                } else if ("searchTours".equals(funcName)) {
                                    result = queryService.searchTours(args.path("keyword").asText(""));
                                }
                            } catch (Exception ex) {
                                log.error("Function call error", ex);
                                result = Map.of("error", ex.getMessage());
                            }

                            contents.add(candidateContent.deepCopy());

                            ObjectNode funcResponseNode = objectMapper.createObjectNode();
                            funcResponseNode.put("name", funcName);
                            ObjectNode responseData = objectMapper.createObjectNode();
                            responseData.put("name", funcName);
                            responseData.set("content", objectMapper.valueToTree(result));
                            funcResponseNode.set("response", responseData);
                            ObjectNode funcResponsePart = objectMapper.createObjectNode();
                            funcResponsePart.set("functionResponse", funcResponseNode);
                            ArrayNode respParts = objectMapper.createArrayNode();
                            respParts.add(funcResponsePart);
                            ObjectNode funcResponseContent = objectMapper.createObjectNode();
                            funcResponseContent.put("role", "function");
                            funcResponseContent.set("parts", respParts);
                            contents.add(funcResponseContent);
                            break;
                        }
                    }
                    if (!hasFunctionCall) {
                        String text = extractText(response);
                        if (text != null && !text.isBlank()) {
                            return text;
                        } else {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
"""

content = re.sub(
    r'JsonNode response = webClient\.post\(\).*?if \(text != null && !text\.isBlank\(\)\) \{\s*return text;\s*\}\s*',
    loop_logic.strip() + '\n            ',
    content,
    flags=re.DOTALL
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

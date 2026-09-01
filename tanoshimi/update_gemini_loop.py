import re

with open(r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\util\GeminiChatClient.java', 'r', encoding='utf-8') as f:
    content = f.read()

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

                            // Append model's call to history
                            contents.add(candidateContent.deepCopy());

                            // Construct functionResponse
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
                            log.warn("Gemini API ?묐떟 ?뺤떇???덉긽怨??щ씪?? {}", response);
                            return "?대씪, 吏€湲??듭떊???????섎꽕... ?좎떆 ???ㅼ떆 留?嫄몄뼱以꾨옒? ?뱻";
                        }
                    }
                } else {
                    break; // no parts
                }
            }
"""

content = re.sub(
    r'JsonNode response = webClient\.post\(\).*?log\.warn\("Gemini API.*?\{\}", response\);',
    loop_logic.strip().replace('\\', '\\\\'), # ensure regex match replace correctly
    content,
    flags=re.DOTALL
)

with open(r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\util\GeminiChatClient.java', 'w', encoding='utf-8') as f:
    f.write(content)

import re

with open(r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\util\GeminiChatClient.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Add import for VivianQueryService
if 'VivianQueryService' not in content:
    content = content.replace('import org.springframework.web.reactive.function.client.WebClient;\n', 'import org.springframework.web.reactive.function.client.WebClient;\nimport net.datasa.tanoshimi.service.VivianQueryService;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport java.util.Map;\n')

    # Add Autowired VivianQueryService
    content = content.replace('private final ObjectMapper objectMapper = new ObjectMapper();', 'private final ObjectMapper objectMapper = new ObjectMapper();\n\n    @Autowired\n    private VivianQueryService queryService;')

tools_part = """
            ArrayNode tools = objectMapper.createArrayNode();
            
            // Add function declarations for VivianQueryService
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
content = re.sub(r'ArrayNode tools = objectMapper\.createArrayNode\(\);.*?body\.set\("tools", tools\);', tools_part, content, flags=re.DOTALL)

with open(r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\util\GeminiChatClient.java', 'w', encoding='utf-8') as f:
    f.write(content)

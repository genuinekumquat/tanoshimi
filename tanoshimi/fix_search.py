import sys

def add_google_search(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find where tools are added
    #             functionDeclarationsNode.set("functionDeclarations", functionDeclarations);
    #            tools.add(functionDeclarationsNode);
    old_code = """            functionDeclarationsNode.set("functionDeclarations", functionDeclarations);
            tools.add(functionDeclarationsNode);"""
            
    new_code = """            functionDeclarationsNode.set("functionDeclarations", functionDeclarations);
            tools.add(functionDeclarationsNode);
            
            // Add Google Search grounding tool so Vivian can recommend external stuff
            ObjectNode googleSearchTool = objectMapper.createObjectNode();
            googleSearchTool.set("googleSearch", objectMapper.createObjectNode());
            tools.add(googleSearchTool);"""
            
    if old_code in content:
        content = content.replace(old_code, new_code)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

add_google_search("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

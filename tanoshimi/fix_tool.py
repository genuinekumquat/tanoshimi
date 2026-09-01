import sys

def fix_geminichatclient(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    old_code = """
            ObjectNode googleSearchTool = objectMapper.createObjectNode();
            googleSearchTool.set("google_search", objectMapper.createObjectNode());
            tools.add(googleSearchTool);"""
            
    content = content.replace(old_code, "")
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_geminichatclient("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

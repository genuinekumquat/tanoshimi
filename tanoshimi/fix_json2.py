import sys

def fix_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    old_code = """String aiResponse = geminiClient.ask(prompt);
            String jsonRaw = aiResponse.trim();
            if (jsonRaw.startsWith("```json")) {
                jsonRaw = jsonRaw.substring(7);
                if (jsonRaw.endsWith("```")) {
                    jsonRaw = jsonRaw.substring(0, jsonRaw.length() - 3);
                }
            } else if (jsonRaw.startsWith("```")) {
                jsonRaw = jsonRaw.substring(3);
                if (jsonRaw.endsWith("```")) {
                    jsonRaw = jsonRaw.substring(0, jsonRaw.length() - 3);
                }
            }
            jsonRaw = jsonRaw.trim();"""
            
    new_code = """String aiResponse = geminiClient.ask(prompt);
            String jsonRaw = aiResponse;
            int startIndex = jsonRaw.indexOf("[");
            int endIndex = jsonRaw.lastIndexOf("]");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                jsonRaw = jsonRaw.substring(startIndex, endIndex + 1);
            }"""
    
    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filename}")
    else:
        print(f"Code not found in {filename}")

fix_file("src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java")

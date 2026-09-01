import sys

def fix_planner(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the readTree part
    old_read = "com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseText);"
    new_read = """// Strip markdown tags if Gemini generated them
            if (responseText.startsWith("```json")) {
                responseText = responseText.replace("```json\\n", "").replace("```json", "");
            }
            if (responseText.startsWith("```")) {
                responseText = responseText.replace("```\\n", "").replace("```", "");
            }
            if (responseText.endsWith("```\\n")) {
                responseText = responseText.substring(0, responseText.length() - 4);
            } else if (responseText.endsWith("```")) {
                responseText = responseText.substring(0, responseText.length() - 3);
            }
            responseText = responseText.trim();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseText);"""
            
    if old_read in content:
        content = content.replace(old_read, new_read)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_planner("src/main/java/net/datasa/tanoshimi/controller/PlannerController.java")

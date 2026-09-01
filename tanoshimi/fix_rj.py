import sys

def fix_real_json(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    old_config = """            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.8);
            body.set("generationConfig", generationConfig);"""
            
    new_config = """            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.8);
            generationConfig.put("response_mime_type", "application/json");
            body.set("generationConfig", generationConfig);"""
            
    if old_config in content:
        content = content.replace(old_config, new_config)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filename}")

fix_real_json("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")

import sys

def fix_model_default(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # replace default fallback
    content = content.replace('@Value("${app.companion.model:gemini-2.5-flash}")', '@Value("${app.companion.model:gemini-1.5-flash}")')
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_model_default("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
fix_model_default("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")

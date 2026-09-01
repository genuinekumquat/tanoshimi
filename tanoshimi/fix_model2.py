import sys

def fix_model_name(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # replace default fallback
    content = content.replace('gemini-1.5-flash', 'gemini-flash-lite-latest')
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_model_name("src/main/resources/application.yml")
fix_model_name("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
fix_model_name("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")

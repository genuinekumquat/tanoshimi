import sys

def shift_model(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Change default to gemini-3.5-flash
    content = content.replace('gemini-flash-lite-latest', 'gemini-3.5-flash-lite')
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

shift_model("src/main/resources/application.yml")
shift_model("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")
shift_model("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

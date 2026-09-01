import sys

def fix_geminichatclient(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    new_content = content.replace('log.error("Gemini API 호출 실패", e);', 'log.error("Gemini API 호출 실패: " + (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ? ((org.springframework.web.reactive.function.client.WebClientResponseException)e).getResponseBodyAsString() : e.getMessage()), e);')
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Fixed {filename}")

fix_geminichatclient("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

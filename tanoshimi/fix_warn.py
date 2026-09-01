import sys

def fix_log_warn(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # I want to change `log.warn("Gemini API 응답 형식이 예상과 달라요");` to `log.warn("Gemini API 응답 형식이 예상과 달라요 : " + response);`
    # However response is a local variable inside the loop! I need to declare it outside.
    
    old_loop_start = """            for (int i = 0; i < 5; i++) {
                body.set("contents", contents);

                JsonNode response = webClient.post()"""
                
    new_loop_start = """            JsonNode lastResponse = null;
            for (int i = 0; i < 5; i++) {
                body.set("contents", contents);

                JsonNode response = webClient.post()"""
                
    if old_loop_start in content:
        content = content.replace(old_loop_start, new_loop_start)
        content = content.replace('if (response == null) break;', 'lastResponse = response;\n                if (response == null) break;')
        content = content.replace('log.warn("Gemini API 응답 형식이 예상과 달라요");', 'log.warn("Gemini API 응답 형식이 예상과 달라요: " + lastResponse);\n            return "오류 내용: " + (lastResponse != null ? lastResponse.toString() : "null");')

    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_log_warn("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

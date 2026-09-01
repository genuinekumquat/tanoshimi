import re

# Fix 1: PlannerController.java - Add dummy pastStyleTags argument
pc_path = "C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java"
with open(pc_path, 'r', encoding='utf-8') as f:
    pc_text = f.read()

pc_text = pc_text.replace("chatbotActivityService.recommend(targetRegion, d, keyword, badWeather)", 
                          "chatbotActivityService.recommend(targetRegion, d, keyword, \"\", badWeather)")

with open(pc_path, 'w', encoding='utf-8') as f:
    f.write(pc_text)

# Fix 2: GeminiChatClient.java - log.warn scope issue
gcc_path = "C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java"
with open(gcc_path, 'r', encoding='utf-8') as f:
    gcc_text = f.read()

gcc_text = gcc_text.replace('log.warn("Gemini API 응답 형식이 예상과 달라요: {}", response);',
                           'log.warn("Gemini API 응답 형식이 예상과 달라요");')

with open(gcc_path, 'w', encoding='utf-8') as f:
    f.write(gcc_text)

print("Java fixes applied.")
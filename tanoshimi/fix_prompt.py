import sys

def fix_prompt(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    old_text = "설정에 없는 내용을 물어보면, 캐릭터의 성격에 맞춰서 그럴싸하게 자연스럽게 대답을 지어내."
    new_text = "설정에 없는 내용을 물어보면, 캐릭터의 성격에 맞춰 그럴싸하게 지어내거나 구글 검색을 활용해 답변해. DB(투어 검색)에 없는 장소나 식당, 외부 여행지도 구글 검색을 통해 자유롭고 적극적으로 추천해줘."
    
    if old_text in content:
        content = content.replace(old_text, new_text)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
        
    print(f"Fixed {filename}")

fix_prompt("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")

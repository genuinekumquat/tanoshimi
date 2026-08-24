import codecs
import re
with codecs.open('src/main/resources/static/js/planner.js', 'r', 'utf-8-sig') as f:
    text = f.read()

# Change the error handling in ask() to display the real error message
old_logic = "if (result.success && result.data.length > 0) {\n                bubble('추천 일정을 찾았습니다! 카드를 계획표로 드래그 해보세요.', 'bot');\n                recCards(result.data);\n            } else {\n                bubble('조건에 맞는 일정을 찾지 못했습니다.', 'bot');\n            }"

new_logic = """if (!result.success) {
                bubble('오류: ' + (result.message || '알 수 없는 서버 에러'), 'bot');
            } else if (result.data && result.data.length > 0) {
                bubble('추천 일정을 찾았습니다! 카드를 계획표로 드래그 해보세요.', 'bot');
                recCards(result.data);
            } else {
                bubble('조건에 맞는 일정을 찾지 못했습니다 (검색어/지역 확인). targetRegion=' + qs.get('region'), 'bot');
            }"""

if old_logic in text:
    text = text.replace(old_logic, new_logic)
else:
    print("Warning: could not find old_logic in planner.js")

with codecs.open('src/main/resources/static/js/planner.js', 'w', 'utf-8') as f:
    f.write(text)

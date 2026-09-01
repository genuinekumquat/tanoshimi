import re

path = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java'

with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = 'prompt.append("참고로, \'source\'가 \'package_default\'인 항목(예: 비행기 시간, 체크인)은 절대 변경 불가능한 요소로 간주하세요.\\n\\n");'
replacement = target + '\n        prompt.append("추가로, 일정 중간에 활동이 없는 빈 시간이 길게 비어있다면, 그 시간대와 동선을 고려해 짧게 즐길 수 있는 추천 활동(유명 카페, 간식, 산책로 등)을 검색하여 일정 브리핑에 꼭 포함해주세요.\\n\\n");'

text = text.replace(target, replacement)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
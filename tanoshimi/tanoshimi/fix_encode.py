import json
import re

with open('dump.json', 'r', encoding='utf-16') as f:
    text = json.loads(f.read())

text = re.sub(r'if \(false\) \{[^}]*\}', '', text)

text = re.sub(r'".*?üũ.*?"', '"숙소 체크인"', text)
text = text.replace('"숙소 체크인"', '"숙소 체크아웃"', 1) # First matched one is checkout (or second)
text = text.replace('"\ufffd \ufffd\ufffd\ufffd"', '"공항 이동"')
text = text.replace('"\ufffd\ufffd\ufffd\ufffd \ufffd\ufffd\ufffd\ufffd \ufffd\ufffd\ufffd\ufffd"', '"공항 버스 이동"')
text = text.replace('"\ufffd\ufffd "', '"이름없는 일정"')

# General replace
text = re.sub(r'"[^"]*\uFFFD[^"]*"', '"알 수 없는 문제 또는 고정된 기본 항목입니다."', text)

with open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', encoding='utf-8') as f:
    f.write(text)

import re

with open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Airport bus
text = re.sub(r'\.title\("공항 버스 [^"]+"\)', '.title("공항 버스 이동")', text)

# 2. Check in
text = re.sub(r'\.title\("[^"]+ 체크[^"]+"\)\s*\n\s*\.priceKrw', '.title("숙소 체크인")\n                        .priceKrw', text)
text = re.sub(r'\.title\("[^"]+ 체크[^"]+"\)\s*\n\s*\.priceKrw', '.title("숙소 체크인")\n                        .priceKrw', text) # just in case
# Wait, check out is different!

# Let's replace by line index or clearer context
lines = text.split('\n')
for i in range(len(lines)):
    if '공항 버스' in lines[i]:
        lines[i] = re.sub(r'".*"', '"공항 버스 이동"', lines[i])
    elif ('체크' in lines[i] or '체크' in lines[i]) and 'priceKrw' not in lines[i]:
        if i > 0 and 'Day 1' in lines[i-3]:  # Wait, let's just find them exactly
            pass

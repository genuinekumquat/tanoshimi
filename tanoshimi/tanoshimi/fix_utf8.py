import json
import re

with open('dump.json', 'r', encoding='utf-8') as f:
    text = json.loads(f.read())

# 1. ensureSchedule
text = re.sub(r'\.title\("+    "\)', '.title("공항 버스 이동")', text)
text = re.sub(r'\.title\(" u? "\)', '.title("숙소 체크인")', text)
text = re.sub(r'\.title\(" u??"\)', '.title("숙소 체크아웃")', text)
text = re.sub(r'\.title\(" ?"\)', '.title("공항 이동")', text)

# 2. addItem
text = re.sub(r'if \(false\) \{\s*throw new BusinessException\(ErrorCode\.SCHEDULE_NOT_DRAFT, " ?? ο ?? ?  ?\."\);\s*\}', '', text)
text = re.sub(r'new BusinessException\(ErrorCode\.INVALID_INPUT, " ? ???\."\)', 'new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 활동입니다.")', text)
text = re.sub(r'\.title\(title == null \|\| title\.isBlank\(\) \? "? " : title\)', '.title(title == null || title.isBlank() ? "이름없는 일정" : title)', text)

# 3. resizeItem
text = re.sub(r'new BusinessException\(ErrorCode\.SCHEDULE_NOT_DRAFT, "? ?   ?? ? ??\."\)', 'new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "기본 패키지 일정은 결제가 완료되어 고정되어 있습니다.")', text)

# 4. removeItem
text = re.sub(r'new BusinessException\(ErrorCode\.SCHEDULE_NOT_DRAFT, "   ???\."\)', 'new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "해당 패키지 일정은 삭제할 수 없습니다.")', text)

with open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', encoding='utf-8') as f:
    f.write(text)

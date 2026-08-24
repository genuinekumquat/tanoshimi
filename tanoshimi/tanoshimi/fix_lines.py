import json

with open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', encoding='utf-8') as f:
    lines = f.read().split('\n')

# The exact lines to replace (0-indexed)
lines[39] = '                    .title("공항 버스 이동")'
lines[49] = '                .title("숙소 체크인")'
lines[61] = '                .title("숙소 체크아웃")'
lines[72] = '                    .title("공항 이동")'
lines[100] = '                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 활동입니다."));'
lines[113] = '                .title(title == null || title.isBlank() ? "이름없는 일정" : title)'
lines[126] = '            throw new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "기본 패키지 일정은 결제가 완료되어 고정되어 있습니다.");'
lines[137] = '            throw new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "기본 패키지 일정은 삭제할 수 없습니다.");'

with open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))

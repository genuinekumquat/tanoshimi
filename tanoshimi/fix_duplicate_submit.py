import re

path_html = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/planner/index.html'
with open(path_html, 'r', encoding='utf-8') as f:
    text_html = f.read()

text_html = text_html.replace(
    '<button type="button" id="btn-submit" class="btn btn-primary btn-sm" th:text="#{planner.submit}" th:attr="data-schedule-id=${schedule.id}">계획표 제출하기</button>',
    '<button type="button" id="btn-submit" class="btn btn-primary btn-sm" th:if="${schedule.status.name() == \'draft\'}" th:text="#{planner.submit}" th:attr="data-schedule-id=${schedule.id}">계획표 제출하기</button>'
)

text_html = text_html.replace(
    '<button type="button" id="btn-pay" class="btn btn-forest btn-sm" style="display:none;">투어결제하기</button>',
    '<button type="button" id="btn-pay" class="btn btn-forest btn-sm" th:style="${schedule.status.name() == \'submitted\' ? \'\' : \'display:none;\'}">투어결제하기</button>'
)

with open(path_html, 'w', encoding='utf-8') as f:
    f.write(text_html)

path_service = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java'
with open(path_service, 'r', encoding='utf-8') as f:
    text_service = f.read()

text_service = text_service.replace(
    'public void submitForPayment(TripScheduleEntity schedule) {\n\n        List<TripScheduleItemEntity>',
    'public void submitForPayment(TripScheduleEntity schedule) {\n        if (!schedule.isDraft()) {\n            throw new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.INVALID_INPUT, "이미 제출 완료된 시간표입니다.");\n        }\n\n        List<TripScheduleItemEntity>'
)

with open(path_service, 'w', encoding='utf-8') as f:
    f.write(text_service)

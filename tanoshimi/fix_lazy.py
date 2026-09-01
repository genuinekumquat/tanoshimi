import re

path_service = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java'
with open(path_service, 'r', encoding='utf-8') as f:
    text_service = f.read()

text_service = text_service.replace(
    'public Long addItem(TripScheduleEntity schedule, UserEntity user, ScheduleItemRequest req) {',
    'public Long addItem(Long scheduleId, UserEntity user, ScheduleItemRequest req) {\n        TripScheduleEntity schedule = scheduleRepository.findById(scheduleId).orElseThrow(() -> new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.SCHEDULE_NOT_FOUND));'
)

with open(path_service, 'w', encoding='utf-8') as f:
    f.write(text_service)

path_controller = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java'
with open(path_controller, 'r', encoding='utf-8') as f:
    text_ctrl = f.read()

text_ctrl = text_ctrl.replace(
    'Long itemId = plannerService.addItem(schedule, user, request);',
    'Long itemId = plannerService.addItem(schedule.getId(), user, request);'
)

with open(path_controller, 'w', encoding='utf-8') as f:
    f.write(text_ctrl)
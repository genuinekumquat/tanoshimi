import os

file_path = 'src/main/java/net/datasa/tanoshimi/controller/PlannerController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

if 'import net.datasa.tanoshimi.util.GeminiClient;' not in text:
    text = text.replace('import net.datasa.tanoshimi.service.*;', 'import net.datasa.tanoshimi.service.*;\nimport net.datasa.tanoshimi.util.GeminiClient;')

if 'private final GeminiClient geminiClient;' not in text:
    text = text.replace('private final SimpMessagingTemplate messagingTemplate;', 'private final SimpMessagingTemplate messagingTemplate;\n    private final GeminiClient geminiClient;')

new_method = '''
    // ===================== [신규] AI 일정 검증 =====================

    @PostMapping(\"/api/planner/{scheduleId}/ai-validate\")
    @ResponseBody
    public ApiResponse<String> aiValidate(@PathVariable Long scheduleId, @RequestParam(defaultValue = \"대중교통\") String mode,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!aiCreditService.tryConsume(user)) {
            throw new BusinessException(ErrorCode.AI_CREDIT_EXCEEDED);
        }
        
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        List<ScheduleItemView> items = plannerService.getItems(schedule);
        
        if (items.isEmpty()) {
            return ApiResponse.ok(\"일정이 비어 있습니다. 항목을 추가한 후 검증해 주세요.\");
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append(\"다음은 여행자의 현재 일정표입니다. 사용자는 '\").append(mode).append(\"'단어를 참고하여 이동합니다.\\n\");
        prompt.append(\"각 일정 항목의 시간과 장소를 분석하여, 선택한 교통수단(자동차 또는 대중교통)으로 물리적으로나 시간상으로 이동이 불가능한 경우가 있는지 검증해주세요.\\n\");
        prompt.append(\"만약 불가능하다면, 'A와 B 둘 다 즐기기는 어렵다. A를 갈 것인가 B를 갈 것인가'와 같이 선택지를 제시하고, 각각의 선택지에 따른 장단점을 비교하는 브리핑을 작성해주세요.\\n\");
        prompt.append(\"참고로, 'source'가 'package_default'인 항목(예: 비행기 시간, 체크인)은 절대 변경 불가능한 요소로 간주하세요.\\n\\n\");
        prompt.append(\"【일정표 데이터】\\n\");
        
        for (ScheduleItemView item : items) {
            String time = String.format(\"%02d:%02d ~ %02d:%02d\", 
                    item.startMinute() / 60, item.startMinute() % 60,
                    (item.startMinute() + item.durationMinute()) / 60,
                    (item.startMinute() + item.durationMinute()) % 60);
            boolean isFixed = \"package_default\".equals(item.source());
            prompt.append(String.format(\"- %d일차 | 시간: %s | 제목: %s | 고정여부: %b\\n\",
                    item.dayIndex(), time, item.title(), isFixed));
        }
        
        String responseText = geminiClient.ask(prompt.toString());
        return ApiResponse.ok(responseText);
    }
'''

if 'aiValidate' not in text:
    text = text.replace('public ApiResponse<Void> submit(@PathVariable Long scheduleId) {', new_method.strip() + '\n\n    @PostMapping(\"/api/planner/{scheduleId}/submit\")\n    @ResponseBody\n    public ApiResponse<Void> submit(@PathVariable Long scheduleId) {')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

print('Updated PlannerController.java')
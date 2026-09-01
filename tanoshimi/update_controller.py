import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java', 'r', encoding='utf-8') as f:
    content = f.read()

old_method_regex = re.compile(r'@PostMapping\(\"/api/planner/\{scheduleId\}/ai-validate\"\).*?return ApiResponse\.ok\(responseText\);\n    \}', re.DOTALL)
old_method = old_method_regex.search(content).group(0)

new_method = '''@PostMapping("/api/planner/{scheduleId}/ai-validate")
    @ResponseBody
    public ApiResponse<Object> aiValidate(@PathVariable Long scheduleId, @RequestParam(defaultValue = "대중교통") String mode,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!aiCreditService.tryConsume(user)) {
            throw new BusinessException(ErrorCode.AI_CREDIT_EXCEEDED);
        }
        
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        List<ScheduleItemView> items = plannerService.getItems(schedule);
        
        if (items.isEmpty()) {
            return ApiResponse.ok(java.util.Map.of("briefing", "일정이 비어 있습니다. 항목을 추가한 후 검증해 주세요."));
        }
        
        // [v16 신규] AI 검증 전 시간표(ai_valid) 임시저장
        lockService.save(scheduleId, user, net.datasa.tanoshimi.domain.entity.SnapshotTrigger.ai_valid);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 여행자의 현재 일정표입니다. 사용자는 '").append(mode).append("'단어를 참고하여 이동합니다.\\n");
        prompt.append("각 일정 항목의 시간과 장소를 분석하여, 선택한 교통수단(자동차 또는 대중교통)으로 물리적으로나 시간상으로 이동이 불가능한 경우가 있는지 검증해주세요.\\n");
        prompt.append("만약 불가능하다면, 'A와 B 둘 다 불가능합니다. A를 가시겠습니까, B를 가시겠습니까?'와 같이 선택지를 제시하고, 각각의 선택지에 따른 장단점을 비교하는 브리핑을 작성해주세요.\\n");
        prompt.append("매일의 1일차, 2일차 등 가장 이른 첫 번째 출발 스케줄 직전에 '호텔 출발' 이라는 일정을 짧게 꼭 추가해줘. 단, 하루가 끝날 때 돌아가는 일정은 만들지 마라.\\n");
        prompt.append("참고로, 'source'가 'package_default'인 항목(예: 비행기 시간, 체크인)은 절대 변경 불가능한 요소로 간주하세요.\\n\\n");
        prompt.append("추가로, 일정 중간에 활동이 없는 빈 시간이 길게 비어있다면, 그 시간대와 동선을 고려해 짧게 즐길 수 있는 추천 활동(유명 카페, 간식, 산책로 등)을 검색하여 일정 브리핑에 꼭 포함해주세요.\\n\\n");
        prompt.append("결과물은 반드시 Markdown을 포함하지 않은 순수 JSON 포맷으로 작성해주세요.\\n");
        prompt.append("{\\"briefing\\": \\"브리핑 내용\\", \\"newSchedule\\": [{\\"dayIndex\\": 1, \\"startMinute\\": 720, \\"durationMinute\\": 60, \\"title\\": \\"...\\", \\"source\\": \\"...\\", \\"activityId\\": null}, ...]}\n");
        prompt.append("newSchedule 배열은 기존 일정을 대체할 새로운 추천 일정 전체 리스트입니다. 주의사항: JSON의 키 값이나 구조를 절대 바꾸지 마세요.\\n\\n");
        prompt.append("【일정표 데이터】\\n");
        
        for (ScheduleItemView item : items) {
            String time = String.format("%02d:%02d ~ %02d:%02d", 
                    item.startMinute() / 60, item.startMinute() % 60,
                    (item.startMinute() + item.durationMinute()) / 60,
                    (item.startMinute() + item.durationMinute()) % 60);
            boolean isFixed = "package_default".equals(item.source());
            prompt.append(String.format("- %d일차 | 시간: %s | 제목: %s | 고정여부: %b | id: %d | source: %s\\n",
                    item.dayIndex(), time, item.title(), isFixed, item.id(), item.source()));
        }
        
        String responseText = geminiClient.ask(prompt.toString());
        responseText = responseText.replaceAll("^`(json)?\\\\s*", "").replaceAll("\\\\s*`$", "");
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseText);
            String briefing = root.path("briefing").asText();
            com.fasterxml.jackson.databind.JsonNode newSched = root.path("newSchedule");
            
            List<TripScheduleItemEntity> allItems = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
            for (TripScheduleItemEntity it : allItems) {
                if (!it.isFixed()) {
                    itemRepository.delete(it);
                }
            }
            itemRepository.flush();
            
            if (newSched != null && newSched.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : newSched) {
                    if ("package_default".equals(node.path("source").asText())) {
                        continue;
                    }
                    Long aid = node.path("activityId").isNull() ? null : node.path("activityId").asLong();
                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,
                            node.path("title").asText(),
                            node.path("memo").isNull() ? null : node.path("memo").asText()
                    );
                    plannerService.addItem(scheduleId, user, req);
                }
            }
            
            broadcast(scheduleId);
            return ApiResponse.ok(java.util.Map.of("briefing", briefing));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.ok(java.util.Map.of("briefing", "응답을 처리하는 중 오류가 발생했습니다: " + e.getMessage() + "\\n\\n원본:\\n" + responseText));
        }
    }'''

content = content.replace(old_method, new_method)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java', 'w', encoding='utf-8') as f:
    f.write(content)

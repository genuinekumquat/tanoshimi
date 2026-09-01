import re
path = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java'

with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

bad_string = """    @PostMapping("/api/planner/{scheduleId}/submit")
    @ResponseBody
    // ===================== [신규] AI 일정 검증 =====================

    @PostMapping("/api/planner/{scheduleId}/ai-validate")
    @ResponseBody"""

good_string = """    // ===================== [신규] AI 일정 검증 =====================

    @PostMapping("/api/planner/{scheduleId}/ai-validate")
    @ResponseBody"""

text = text.replace(bad_string, good_string)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
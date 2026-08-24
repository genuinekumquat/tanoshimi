import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/controller/PlannerController.java', 'r', 'utf-8-sig') as f:
    text = f.read()

import re

text = re.sub(
    r'Long itemId = plannerService\.addItem\(schedule, user, request\);\s*broadcast\(scheduleId\);',
    'Long itemId = plannerService.addItem(schedule, user, request);\n        try { broadcast(scheduleId); } catch(Exception ignored) {}',
    text
)

text = re.sub(
    r'plannerService\.removeItem\(itemId\);\s*broadcast\(schedule\.getId\(\)\);',
    'plannerService.removeItem(itemId);\n        try { broadcast(schedule.getId()); } catch(Exception ignored) {}',
    text
)

with codecs.open('src/main/java/net/datasa/tanoshimi/controller/PlannerController.java', 'w', 'utf-8') as f:
    f.write(text)

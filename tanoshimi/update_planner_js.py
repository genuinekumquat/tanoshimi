import re

js_path_src = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/planner.js'
js_path_build = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/planner.js'

with open(js_path_src, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update onDropToColumn
old_drop_api = """        if (data.kind === 'recommend' || data.kind === 'custom') {
            await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                dayIndex: day, startMinute, durationMinute: data.durationMin,
                activityId: data.activityId || null, title: data.title || null, memo: null
            });
        }"""
new_drop_api = """        if (data.kind === 'recommend' || data.kind === 'custom') {
            const parsedDuration = parseInt(data.durationMin, 10);
            const finalDuration = (!isNaN(parsedDuration) && parsedDuration > 0) ? parsedDuration : 60;
            const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                dayIndex: day, startMinute, durationMinute: finalDuration,
                activityId: data.activityId || null, title: data.title || '새 일정', memo: null
            });
            if (!res.success && res.message) alert(res.message);
        }"""

text = text.replace(old_drop_api, new_drop_api)

# 2. Update .put button logic in recCards
# It looks like:
#               card.querySelector('.put').addEventListener('click', async () => {
#                   await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
#                       dayIndex: 1, startMinute: 12*60, durationMinute: item.durationMin,
#                       activityId: item.activityId || null, title: item.title, memo: item.description || null
#                   });
#                   await reload();
#               });
# We'll regex replace this part safely to catch any variance
import re
def replace_put(m):
    return m.group(0).replace("durationMinute: item.durationMin", "durationMinute: (!isNaN(parseInt(item.durationMin)) && parseInt(item.durationMin) > 0) ? parseInt(item.durationMin) : 60") \
                     .replace("await window.api.post", "const res = await window.api.post") \
                     .replace("});\n                  await reload();", "});\n                  if(!res.success && res.message) { alert(res.message); } else { await reload(); }")

# Actually simply string replace
text = re.sub(r'card\.querySelector\(\'\.put\'\)\.addEventListener\(\'click\', async \(\) => \{.+?await reload\(\);\s+\}\);', 
              r"""card.querySelector('.put').addEventListener('click', async () => {
                  const finalDuration = (!isNaN(parseInt(item.durationMin)) && parseInt(item.durationMin) > 0) ? parseInt(item.durationMin) : 60;
                  const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                      dayIndex: 1, startMinute: 12*60, durationMinute: finalDuration,
                      activityId: item.activityId || null, title: item.title || '새 일정', memo: item.description || null
                  });
                  if(!res.success && res.message) { alert(res.message); } else { await reload(); }
              });""", text, flags=re.DOTALL)

with open(js_path_src, 'w', encoding='utf-8') as f:
    f.write(text)
with open(js_path_build, 'w', encoding='utf-8') as f:
    f.write(text)

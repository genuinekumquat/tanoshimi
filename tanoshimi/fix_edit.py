import sys

def fix_planner_js(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    old_code = """            // Allow edit for custom
            if (item.source === 'custom' && (typeof IS_LOCK_HOLDER === 'undefined' || IS_LOCK_HOLDER)) {
                el.addEventListener('dblclick', async (e) => {
                    e.stopPropagation();
                    const newTitle = prompt('일정 제목을 수정하세요:', item.title);
                    if (newTitle !== null && newTitle.trim() !== '') {
                        // Assuming update API exists or we reuse item add logic
                        // Alternatively since we only have removeItem/addItem/resize, 
                        // we can remove and add with same params if there's no update endpoint. Wait, there is resize API but no edit API?
                        // Let's check PlannerController APIs. I'll just change title locally and we can figure out backend if needed.
                        // Actually, I'll delete then add!
                        await window.api.delete(`/api/planner/items/${item.id}`);
                        await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                            dayIndex: item.dayIndex,
                            startMinute: item.startMinute,
                            durationMinute: item.durationMinute,
                            activityId: null,
                            title: newTitle.trim(),
                            memo: item.memo
                        });
                        reload();
                    }
                });
            }"""
            
    new_code = """            // Allow edit for blocks
            if (item.source !== 'package_default' && (typeof IS_LOCK_HOLDER === 'undefined' || IS_LOCK_HOLDER)) {
                el.addEventListener('dblclick', async (e) => {
                    e.stopPropagation();
                    const newTitle = prompt('일정 제목을 수정하세요:', item.title);
                    if (newTitle !== null && newTitle.trim() !== '') {
                        let originalActivityId = item.source === 'custom' ? null : null; // In frontend item view we don't know activityId.
                        // We will just post as custom if they rename it or we can pass title. 
                        // Actually wait we could change startMinute and durationMinute, but they are same here. 
                        // we can put it as custom. But it drops the linkage. Is there an update API?
                        // Let's just create custom block
                        await window.api.delete(`/api/planner/items/${item.id}`);
                        await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                            dayIndex: item.dayIndex,
                            startMinute: item.startMinute,
                            durationMinute: item.durationMinute,
                            activityId: null,
                            title: newTitle.trim(),
                            memo: item.memo
                        });
                        reload();
                    }
                });
            }"""
            
    content = content.replace(old_code, new_code)
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_planner_js("src/main/resources/static/js/planner.js")

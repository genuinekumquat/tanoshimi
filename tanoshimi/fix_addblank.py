import sys

def fix_planner_js_addblank(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    orig_add_blank = """    async function addBlank(dayKey) {
        const title = prompt('일정 이름을 입력하세요.', '새로운 일정');
        if (!title) return;
        const day = parseInt(dayKey.replace('d', ''), 10) || 1;
        await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
            dayIndex: day, startMinute: 12 * 60, durationMinute: 60, activityId: null, title: title, memo: null
        });
        await reload();
    }"""
    
    new_add_blank = """    function addBlank(dayKey) {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        const day = parseInt(dayKey.replace('d', ''), 10) || 1;
        showEditModal('', '', '#4b6b4a', async (newTitle, newMemo, newColor) => {
            if (newTitle !== '') {
                const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                    dayIndex: day,
                    startMinute: 12 * 60, // 12:00 PM
                    durationMinute: 60,
                    activityId: null,
                    title: newTitle,
                    memo: newMemo,
                    color: newColor
                });
                if (res.success) { await reload(); }
            }
        });
    }"""
    
    if orig_add_blank in content:
        content = content.replace(orig_add_blank, new_add_blank)

    # I also see gridHead.querySelectorAll('.add').forEach(btn => btn.addEventListener('click', async () => { await addBlank(btn.dataset.day); }))
    # which implies async addBlank, but now it returns nothing. It's okay.

    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated planner JS {filename}")

fix_planner_js_addblank("src/main/resources/static/js/planner.js")

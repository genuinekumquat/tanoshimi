import sys

def fix_planner_js_render(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    orig = """            el.innerHTML = `
              <div class="t">${escapeHtml(item.title)}</div>
              <div class="bg"></div>
              ${item.source !== 'package_default' ? '<div class="del">X</div>' : ''}
              ${item.source !== 'package_default' ? '<div class="grip"></div>' : ''}
            `;"""
            
    new_html = """            let memoHtml = item.memo ? `<div class="m" style="font-size:10px; opacity:0.85; margin-top:1px; line-height:1.2; word-break:keep-all;">${escapeHtml(item.memo)}</div>` : '';
            el.innerHTML = `
              <div class="t" style="color: ${item.color || ''}; font-weight: 700;">${escapeHtml(item.title)}</div>
              ${memoHtml}
              <div class="bg"></div>
              ${item.source !== 'package_default' ? '<div class="del">X</div>' : ''}
              ${item.source !== 'package_default' ? '<div class="grip"></div>' : ''}
            `;"""
            
    if orig in content:
        content = content.replace(orig, new_html)

    # also update addBlank to use modal
    orig_add_blank = """    async function addBlank(dayKey) {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        const dIdx = parseInt(dayKey.replace('d', ''));
        const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
            dayIndex: dIdx,
            startMinute: 600, // 10:00 AM
            durationMinute: 60,
            activityId: null
        });
        if (res.success) { await reload(); }
    }"""
    
    new_add_blank = """    function addBlank(dayKey) {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        const dIdx = parseInt(dayKey.replace('d', ''));
        showEditModal('', '', '#4b6b4a', async (newTitle, newMemo, newColor) => {
            if (newTitle !== '') {
                const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                    dayIndex: dIdx,
                    startMinute: 600, // 10:00 AM
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

    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated planner JS {filename}")

fix_planner_js_render("src/main/resources/static/js/planner.js")

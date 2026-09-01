import re

file_path = r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\resources\static\js\planner.js'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

rec_cards_new = """    function recCards(list) {
        const chat = document.getElementById('chat');
        if (!chat || !list.length) return;
        const wrap = document.createElement('div');
        wrap.className = 'rec-list';
        list.forEach(item => {
            const card = document.createElement('div');
            card.className = 'rec-card';
            card.draggable = true;
            card.dataset.payload = JSON.stringify({ 
                kind: item.kind || 'recommend', 
                activityId: item.activityId || null, 
                title: item.title,
                durationMin: item.durationMin 
            });
            const priceText = item.priceKrw ? ` \\\\${item.priceKrw.toLocaleString()}` : '';
            card.innerHTML = `
              <span class="sw" style="background:var(--cat-festival)"></span>
              <span class="info">
                <span class="t">${escapeHtml(item.title)}</span>
                <span class="m">${item.durationMin}분 소요${priceText}</span>
              </span>
              <button class="put" title="계획표에 추가">+</button>`;

            card.addEventListener('dragstart', e => {
                e.dataTransfer.setData('text/plain', card.dataset.payload);
            });
            card.querySelector('.put').addEventListener('click', async () => {
                await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                    dayIndex: 1, startMinute: 12*60, durationMinute: item.durationMin,
                    activityId: item.activityId || null, title: item.title, memo: item.description || null
                });
                await reload();
            });
            wrap.appendChild(card);
        });
        chat.appendChild(wrap);
        chat.scrollTop = chat.scrollHeight;
    }"""

content = re.sub(
    r'function recCards\(list\) \{.*? chat\.scrollTop = chat\.scrollHeight;\s*\}',
    rec_cards_new.strip(),
    content,
    flags=re.DOTALL
)

drop_logic = """        if (data.kind === 'recommend' || data.kind === 'custom') {
            await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                dayIndex: day, startMinute, durationMinute: data.durationMin,
                activityId: data.activityId || null, title: data.title || null, memo: null
            });
        } else if (data.id) {"""

content = re.sub(
    r'if \(data\.kind === \'recommend\'\) \{\s*await window\.api\.post\(`/api/planner/\$\{SCHEDULE_ID\}/items`, \{\s*dayIndex: day, startMinute, durationMinute: data\.durationMin,\s*activityId: data\.activityId, title: null, memo: null\s*\}\);\s*\} else if \(data\.id\) \{',
    drop_logic.strip(),
    content,
    flags=re.DOTALL
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

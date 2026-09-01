import sys

def add_modal_to_planner_js(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Define a helper function to show a custom modal for Title, Memo, and Color
    modal_js = """
    function showEditModal(initialTitle, initialMemo, initialColor, callback) {
        const overlay = document.createElement('div');
        overlay.style.position = 'fixed';
        overlay.style.top = '0'; overlay.style.left = '0';
        overlay.style.width = '100vw'; overlay.style.height = '100vh';
        overlay.style.backgroundColor = 'rgba(0,0,0,0.5)';
        overlay.style.display = 'flex';
        overlay.style.justifyContent = 'center';
        overlay.style.alignItems = 'center';
        overlay.style.zIndex = '99999';
        
        const modal = document.createElement('div');
        modal.style.background = '#fff';
        modal.style.padding = '24px';
        modal.style.borderRadius = '12px';
        modal.style.width = '320px';
        modal.style.boxShadow = '0 8px 32px rgba(0,0,0,0.2)';
        
        const titleEl = document.createElement('h3');
        titleEl.textContent = '일정 정보 입력';
        titleEl.style.marginTop = '0';
        titleEl.style.marginBottom = '16px';
        
        const tLabel = document.createElement('label');
        tLabel.textContent = '제목';
        tLabel.style.display = 'block'; tLabel.style.fontSize = '12px';
        const tInput = document.createElement('input');
        tInput.type = 'text'; tInput.value = initialTitle || '';
        tInput.style.width = '100%'; tInput.style.marginBottom = '12px';
        tInput.style.padding = '8px'; tInput.style.boxSizing = 'border-box';
        
        const mLabel = document.createElement('label');
        mLabel.textContent = '내용 (메모)';
        mLabel.style.display = 'block'; mLabel.style.fontSize = '12px';
        const mInput = document.createElement('textarea');
        mInput.value = initialMemo || '';
        mInput.style.width = '100%'; mInput.style.marginBottom = '12px';
        mInput.style.height = '60px';
        mInput.style.padding = '8px'; mInput.style.boxSizing = 'border-box';
        
        const cLabel = document.createElement('label');
        cLabel.textContent = '글자색';
        cLabel.style.display = 'block'; cLabel.style.fontSize = '12px';
        const cInput = document.createElement('input');
        cInput.type = 'color'; cInput.value = initialColor || '#4b6b4a';
        cInput.style.width = '100%'; cInput.style.marginBottom = '20px';
        cInput.style.height = '36px';
        
        const btnDiv = document.createElement('div');
        btnDiv.style.display = 'flex'; btnDiv.style.justifyContent = 'flex-end'; btnDiv.style.gap = '8px';
        
        const cancelBtn = document.createElement('button');
        cancelBtn.textContent = '취소';
        cancelBtn.style.padding = '6px 12px';
        cancelBtn.onclick = () => document.body.removeChild(overlay);
        
        const okBtn = document.createElement('button');
        okBtn.textContent = '확인';
        okBtn.style.padding = '6px 12px';
        okBtn.style.background = 'var(--forest)';
        okBtn.style.color = '#fff';
        okBtn.style.border = 'none';
        okBtn.style.borderRadius = '4px';
        okBtn.onclick = () => {
            document.body.removeChild(overlay);
            callback(tInput.value.trim(), mInput.value.trim(), cInput.value);
        };
        
        btnDiv.appendChild(cancelBtn);
        btnDiv.appendChild(okBtn);
        
        modal.appendChild(titleEl);
        modal.appendChild(tLabel);
        modal.appendChild(tInput);
        modal.appendChild(mLabel);
        modal.appendChild(mInput);
        modal.appendChild(cLabel);
        modal.appendChild(cInput);
        modal.appendChild(btnDiv);
        
        overlay.appendChild(modal);
        document.body.appendChild(overlay);
    }
"""

    if 'function showEditModal' not in content:
        content = modal_js + content

    old_dblclick = """                el.addEventListener('dblclick', async (e) => {
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
                });"""
                
    new_dblclick = """                el.addEventListener('dblclick', async (e) => {
                    e.stopPropagation();
                    showEditModal(item.title, item.memo, item.color || '#4b6b4a', async (newTitle, newMemo, newColor) => {
                        if (newTitle !== '') {
                            await window.api.delete(`/api/planner/items/${item.id}`);
                            await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                                dayIndex: item.dayIndex,
                                startMinute: item.startMinute,
                                durationMinute: item.durationMinute,
                                activityId: null,
                                title: newTitle,
                                memo: newMemo,
                                color: newColor
                            });
                            reload();
                        }
                    });
                });"""

    content = content.replace(old_dblclick, new_dblclick)

    # We also need to apply the color to the block text
    # The block's text is the innerHTML or textContent
    # In planner.js, it's defined like this:
    # el.innerHTML = `
    #    <div class="name">${escapeHtml(item.title)}</div>
    #    <div class="meta">${item.durationMinute}분 ${priceText}</div>
    #    ${memoHtml}
    
    # We should add a listener for btn-add and btn-clear
    btn_add_logic = """
    document.getElementById('btn-add')?.addEventListener('click', () => {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        showEditModal('', '', '#4b6b4a', async (newTitle, newMemo, newColor) => {
            if (newTitle !== '') {
                await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                    dayIndex: 0,
                    startMinute: 600, // default 10:00 AM
                    durationMinute: 60,
                    activityId: null,
                    title: newTitle,
                    memo: newMemo,
                    color: newColor
                });
                reload();
            }
        });
    });

    document.getElementById('btn-clear')?.addEventListener('click', async () => {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        if (confirm('자동 일정을 제외한 모든 커스텀/추천 일정을 지웁니다. 초기화하시겠습니까?')) {
            // we delete everything non-fixed
            for (const item of items) {
                if (item.source !== 'package_default') {
                    await window.api.delete(`/api/planner/items/${item.id}`);
                }
            }
            reload();
        }
    });

    buildGrid();"""
    
    content = content.replace('buildGrid();', btn_add_logic)

    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated planner JS {filename}")

add_modal_to_planner_js("src/main/resources/static/js/planner.js")

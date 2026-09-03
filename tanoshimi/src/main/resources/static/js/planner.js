
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
(function () {
    const SLOT_MIN = 30;         // 30 min slots
    const SLOT_H = 110;           // px per slot

    // DAYS and SLOTS depend on schedule
    const START_HOUR = 6;
    const END_HOUR = 24;
    const SLOTS = (END_HOUR - START_HOUR) * (60 / SLOT_MIN);
    let DAY_COUNT = typeof SCHEDULE_DAYS !== 'undefined' ? SCHEDULE_DAYS : (typeof DURATION_NIGHTS !== 'undefined' ? DURATION_NIGHTS + 1 : 4);


        let DAYS = [];
    function buildDays() {
        DAYS = Array.from({length: Math.max(DAY_COUNT, 1)}, (_, i) => ({
            key: 'd' + (i + 1),
            date: (i + 1) + '일차',
            week: ''
        }));
    }
    buildDays();

    let items = [];

    function slotToTime(slot) {
        const total = START_HOUR * 60 + slot * SLOT_MIN;
        const h = Math.floor(total / 60);
        const m = total % 60;
        const tStr = `${h}:${m.toString().padStart(2, '0')}`;
        return m === 0 ? `<strong>${tStr}</strong>` : `<span style="font-weight:normal;opacity:0.8;">${tStr}</span>`;
    }

    const gridHead = document.getElementById('grid-head');
    const gridBody = document.getElementById('grid-body');

    function buildGrid() {
        if (!gridHead || !gridBody) return;
        
        // head
        gridHead.innerHTML = '<div class="corner"></div>' + DAYS.map(d => `
          <div class="day-head">
            <div class="d">${d.date}</div>
            <div class="w">${d.week}</div>
            <button class="add" data-day="${d.key}">+ 추가</button>
          </div>`).join('');
          
        gridHead.querySelectorAll('.add').forEach(btn =>
          btn.addEventListener('click', async () => {
              await addBlank(btn.dataset.day);
          })
        );
        
        // body
        let bodyHtml = '<div class="time-col">';
        for(let i=0; i<SLOTS; i++) {
            bodyHtml += `<div class="slot">${slotToTime(i)}</div>`;
        }
        bodyHtml += '</div>';

        DAYS.forEach(d => {
            bodyHtml += `<div class="col" data-day="${d.key}">`;
            for(let i=0; i<SLOTS; i++) {
                bodyHtml += `<div class="cell"></div>`;
            }
            bodyHtml += `</div>`;
        });
        gridBody.innerHTML = bodyHtml;

        gridBody.querySelectorAll('.col').forEach(col => {
            col.addEventListener('dragover', e => e.preventDefault());
            col.addEventListener('drop', onDropToColumn);
        });
    }

    /* ---------------------------------------------------------------------
       렌더링
       --------------------------------------------------------------------- */
    function render() {
        document.querySelectorAll('.col').forEach(col => {
            col.querySelectorAll('.block').forEach(el => el.remove());
        });

        let totalKrw = 0, totalJpy = 0;
        items.forEach(item => {
            const startSlot = (item.startMinute - START_HOUR * 60) / SLOT_MIN;
            const lenSlot = item.durationMinute / SLOT_MIN;
            if (startSlot < 0 || startSlot >= SLOTS) return; // 화면 밖

            const dtKey = "d" + item.dayIndex;
            const col = document.querySelector(`.col[data-day="${dtKey}"]`);
            if (!col) return;

            const el = document.createElement('div');
            el.className = 'block';
            el.dataset.id = item.id;
            // 편집권이 없으면(IS_LOCK_HOLDER=false) 아무 블록도 드래그할 수 없다 - 전원 읽기전용 원칙.
            el.draggable = item.source !== 'package_default' && (typeof IS_LOCK_HOLDER === 'undefined' || IS_LOCK_HOLDER);
            el.style.top = (startSlot * SLOT_H + 1) + 'px';
            el.style.height = (lenSlot * SLOT_H - 3) + 'px';

            if (item.source === 'package_default') {
                el.classList.add('pkg');
            } else if (item.source === 'custom') {
                el.classList.add('blank');
            } else {
                el.classList.add('act');
            }

            let memoHtml = item.memo ? `<div class="m" style="font-size:10px; opacity:0.85; margin-top:1px; line-height:1.2; word-break:keep-all;">${escapeHtml(item.memo)}</div>` : '';
            el.innerHTML = `
              <div class="t" style="color: ${item.color || 'var(--custom-text-color, #4b6b4a)'}; font-weight: 700;">${escapeHtml(item.title)}</div>
              ${memoHtml}
              <div class="bg"></div>
              ${item.source !== 'package_default' ? '<div class="del">X</div>' : ''}
              ${item.source !== 'package_default' ? '<div class="grip"></div>' : ''}
            `;
            col.appendChild(el);

            totalKrw += item.priceKrw || 0;
            totalJpy += item.priceJpy || 0;

            if (item.source !== 'package_default') {
                el.addEventListener('dragstart', (e) => {
                    e.dataTransfer.setData('text/plain', JSON.stringify({id: item.id}));
                });
                const delBtn = el.querySelector('.del');
                if (delBtn) delBtn.addEventListener('click', () => removeBlock(item));

                const grip = el.querySelector('.grip');
                if (grip) grip.addEventListener('pointerdown', (e) => startResize(e, item, el, startSlot, lenSlot));
            }
            
            // Allow edit for blocks
            if (item.source !== 'package_default' && (typeof IS_LOCK_HOLDER === 'undefined' || IS_LOCK_HOLDER)) {
                el.addEventListener('dblclick', async (e) => {
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
                });
            }
        });

        const totalEl = document.getElementById('total-cost');
        if (totalEl) {
            totalEl.textContent = `합계: \\${totalKrw.toLocaleString()} / ¥${totalJpy.toLocaleString()}`;
        }
    }

    async function removeBlock(item) {
        if (!confirm('삭제하시겠습니까?')) return;
        await window.api.del(`/api/planner/items/${item.id}`);
        await reload();
    }

    /* ---------------------------------------------------------------------
       크기 조절 및 드래그 앤 드롭
       --------------------------------------------------------------------- */
    function startResize(e, item, el, startSlot, startLen) {
        e.preventDefault(); e.stopPropagation();
        el.draggable = false;
        const startY = e.clientY;
        el.setPointerCapture(e.pointerId);

        let finalLenMin = item.durationMinute;

        function move(ev) {
            const delta = Math.round((ev.clientY - startY) / SLOT_H);
            const rawLen = startLen + delta;
            const newLenMin = Math.max(1, rawLen) * SLOT_MIN;
            finalLenMin = newLenMin;
            el.style.height = (Math.max(1, rawLen) * SLOT_H - 3) + 'px';
        }
        function up() {
            el.removeEventListener('pointermove', move);
            el.removeEventListener('pointerup', up);
            el.draggable = true;
            window.api.patch(`/api/planner/items/${item.id}?startMinute=${item.startMinute}&durationMinute=${finalLenMin}`)
                .then(res => {
                    if (!res.success && res.message) alert(res.message);
                    reload();
                });
        }
        el.addEventListener('pointermove', move);
        el.addEventListener('pointerup', up);
    }

    async function onDropToColumn(e) {
        e.preventDefault();
        const col = e.currentTarget;
        col.classList.remove('over');
        const day = parseInt(col.dataset.day.replace('d', ''), 10);
        
        let data;
        try { data = JSON.parse(e.dataTransfer.getData('text/plain')); } catch(_) { return; }
        
        const rawSlot = Math.round((e.clientY - col.getBoundingClientRect().top) / SLOT_H);
        const startMinute = START_HOUR * 60 + Math.max(0, rawSlot) * SLOT_MIN;

        if (data.kind === 'recommend' || data.kind === 'custom') {
            const parsedDuration = parseInt(data.durationMin, 10);
            const finalDuration = (!isNaN(parsedDuration) && parsedDuration > 0) ? parsedDuration : 60;
            const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                dayIndex: day, startMinute, durationMinute: finalDuration,
                activityId: data.activityId || null, title: data.title || '새 일정', memo: null
            });
            if (!res.success && res.message) alert(res.message);
        } else if (data.id) {
            const existing = items.find(i => i.id === data.id);
            if (!existing) return;
            await window.api.patch(`/api/planner/items/${data.id}?startMinute=${startMinute}&durationMinute=${existing.durationMinute}&dayIndex=${day}`)
                .then(res => {
                    if (!res.success && res.message) alert(res.message);
                });
        }
        await reload();
    }

    /* ---------------------------------------------------------------------
       일정 추가
       --------------------------------------------------------------------- */
    async function addBlank(dayKey) {
        const title = prompt('항목 이름을 입력하세요', '새로운 일정');
        if (!title) return;
        const day = parseInt(dayKey.replace('d', ''), 10) || 1;
        await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
            dayIndex: day, startMinute: 12 * 60, durationMinute: 60, activityId: null, title: title, memo: null
        });
        await reload();
    }

    /* ---------------------------------------------------------------------
       AI 추천 챗봇
       --------------------------------------------------------------------- */
    function bubble(text, who) {
        const c = document.getElementById('chat');
        if (!c) return;
        const div = document.createElement('div');
        div.className = 'msg ' + who;
        div.textContent = text;
        c.appendChild(div);
        c.scrollTop = c.scrollHeight;
        return div;
    }

    function recCards(list) {
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
            const priceText = item.priceKrw ? ` \${item.priceKrw.toLocaleString()}` : '';
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
                  const finalDuration = (!isNaN(parseInt(item.durationMin)) && parseInt(item.durationMin) > 0) ? parseInt(item.durationMin) : 60;
                  const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                      dayIndex: 1, startMinute: 12*60, durationMinute: finalDuration,
                      activityId: item.activityId || null, title: item.title || '새 일정', memo: item.description || null
                  });
                  if(!res.success && res.message) { alert(res.message); } else { await reload(); }
              });
            wrap.appendChild(card);
        });
        chat.appendChild(wrap);
        chat.scrollTop = chat.scrollHeight;
    }

    async function ask(text, keywordOverride = null) {
        if (!text.trim() && !keywordOverride && keywordOverride !== '') return;
        if (text) bubble(text, 'me');
        const inp = document.getElementById('ai-text');
        if (inp) inp.value = '';

        const chat = document.getElementById('chat');
        if (!chat) return;
        const typing = document.createElement('div');
        typing.className = 'msg bot typing';
        typing.innerHTML = '<span></span><span></span><span></span>';
        chat.appendChild(typing);
        chat.scrollTop = chat.scrollHeight;

        try {
            const qs = new URLSearchParams({ date: '2027-10-14', region: TOUR_REGION });
            if (keywordOverride !== null) {
                if (keywordOverride) qs.set('keyword', keywordOverride);
            } else {
                qs.set('keyword', text);
            }
            const result = await window.api.get(`/api/planner/${SCHEDULE_ID}/recommend?` + qs.toString());
            typing.remove();
            
            if (!result.success) {
                bubble('오류: ' + (result.message || '알 수 없는 서버 에러'), 'bot');
            } else if (result.data && result.data.length > 0) {
                bubble('추천 일정을 찾았습니다! 카드를 계획표로 드래그 해보세요.', 'bot');
                recCards(result.data);
            } else {
                bubble('조건에 맞는 일정을 찾지 못했습니다 (검색어/지역 확인).', 'bot');
            }
        } catch(e) {
            typing.remove();
            bubble('오류가 발생했습니다.', 'bot');
        }
    }

    document.body.addEventListener('click', e => {
        if (e.target.closest('#ai-send')) {
            const input = document.getElementById('ai-text');
            if (input) ask(input.value);
        }
    });

    document.body.addEventListener('keydown', e => {
        if (e.target.id === 'ai-text' && e.key === 'Enter') {
            ask(e.target.value);
        }
    });

    document.querySelectorAll('#quick button').forEach(b => {
        b.addEventListener('click', () => ask(b.textContent, ''));
    });

    /* ---------------------------------------------------------------------
       데이터 로딩 및 소켓
       --------------------------------------------------------------------- */
    async function reload() {
        const result = await window.api.get(`/api/planner/${SCHEDULE_ID}/items`);
        if (result.success) {
            items = result.data;
            render();
        }
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[c]));
    }

    function connectRealtime() {
        if (typeof SockJS === 'undefined') return;
        const socket = new SockJS('/ws');
        const stomp = Stomp.over(socket);
        stomp.debug = null;
        stomp.connect({}, () => {
            stomp.subscribe(`/topic/planner/${SCHEDULE_ID}`, (message) => {
                items = JSON.parse(message.body);
                render();
            });
        });
    }

    const cp = document.getElementById('custom-slot-color');
    if (cp) {
        cp.value = localStorage.getItem('custom-slot-color') || '#ffa500';
        document.documentElement.style.setProperty('--custom-slot-color', cp.value);
        cp.addEventListener('input', e => {
            localStorage.setItem('custom-slot-color', e.target.value);
            document.documentElement.style.setProperty('--custom-slot-color', e.target.value);
        });
    }

    
    
    document.getElementById('btn-add-day')?.addEventListener('click', async () => {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        DAY_COUNT++;
        buildDays();
        buildGrid();
        await window.api.patch(`/api/planner/${SCHEDULE_ID}/days?days=${DAY_COUNT}`);
        reload();
    });
    document.getElementById('btn-remove-day')?.addEventListener('click', async () => {
        if (typeof IS_LOCK_HOLDER !== 'undefined' && !IS_LOCK_HOLDER) return;
        if (DAY_COUNT <= 1) {
            alert('최소 1일은 있어야 합니다.');
            return;
        }
        if (confirm(`정말 마지막 날(${DAY_COUNT}일차)을 삭제하시겠습니까? 해당 일차의 모든 일정이 삭제될 수 있습니다.`)) {
            DAY_COUNT--;
            buildDays();
            buildGrid();
            await window.api.patch(`/api/planner/${SCHEDULE_ID}/days?days=${DAY_COUNT}`);
            reload();
        }
    });
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

        const ctc = document.getElementById('custom-text-color');
    if (ctc) {
        ctc.value = localStorage.getItem('custom-text-color') || '#4b6b4a';
        document.documentElement.style.setProperty('--custom-text-color', ctc.value);
        ctc.addEventListener('input', e => {
            localStorage.setItem('custom-text-color', e.target.value);
            document.documentElement.style.setProperty('--custom-text-color', e.target.value);
            // Also override the inline style of all blocks if they were using the global one
            document.querySelectorAll('.block .t').forEach(el => {
                if (!el.style.color || el.style.color === 'inherited' || el.style.color === '') {
                    el.style.color = e.target.value;
                }
            });
        });
    }

    buildGrid();
    reload();
    connectRealtime();
    
    document.getElementById('btn-submit')?.addEventListener('click', async () => {
        if (!confirm('제출하시겠습니까?')) return;
        const r = await window.api.post(`/api/planner/${SCHEDULE_ID}/submit`, {});
        alert(r.message);
        if (r.success) location.reload();
    });

    document.getElementById('btn-ai-validate')?.addEventListener('click', async () => {
        const modeElem = document.getElementById('transit-mode');
        const mode = modeElem && modeElem.value === 'car' ? '자동차' : '대중교통';
        const btn = document.getElementById('btn-ai-validate');
        const oldText = btn.textContent;
        btn.textContent = '검증 중...';
        btn.disabled = true;

        const vBubble = document.getElementById('companion-speech-bubble');

        try {
            if (vBubble) {
                if (window.companionTimeout) clearTimeout(window.companionTimeout);
                vBubble.innerText = `${mode} 기준으로 일정을 검증하고 있어요...`;
                vBubble.style.display = 'block';
            }
            const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/ai-validate?mode=${encodeURIComponent(mode)}`, {});
            if (res.success) {
                if (vBubble) {
                    vBubble.innerText = res.data.briefing;
                    if (window.companionTimeout) clearTimeout(window.companionTimeout);
                }
                await reload();
            } else {
                if (vBubble) {
                    vBubble.innerText = '검증에 실패했습니다. ' + (res.message || '');
                }
            }
        } catch (e) {
            if (vBubble) {
                vBubble.innerText = '오류가 발생했습니다.';
            }
        } finally {
            btn.textContent = oldText;
            btn.disabled = false;
        }
    });

})();

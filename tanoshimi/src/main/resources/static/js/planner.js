(function () {
    const SLOT_MIN = 30;         // 30 min slots
    const SLOT_H = 110;           // px per slot

    // DAYS and SLOTS depend on schedule
    const START_HOUR = 6;
    const END_HOUR = 24;
    const SLOTS = (END_HOUR - START_HOUR) * (60 / SLOT_MIN);
    const DAY_COUNT = typeof DURATION_NIGHTS !== 'undefined' ? DURATION_NIGHTS + 1 : 4;

    const DAYS = Array.from({length: DAY_COUNT}, (_, i) => ({
        key: 'd' + (i + 1),
        date: (i + 1) + '일',
        week: ''
    }));

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

            el.innerHTML = `
              <div class="t">${escapeHtml(item.title)}</div>
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

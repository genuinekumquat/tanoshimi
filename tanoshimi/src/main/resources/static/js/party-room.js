/**
 * 파티 전용 페이지 - 실시간 파티채팅(STOMP) + 투표 + 신청서 승인/거절.
 * 번역 버튼: 서버가 구현한 번역 클라이언트로 접근하여 채팅 내용을 번역합니다.
 */
(function () {
    // ---------------- 채팅 ----------------
    if (ROOM_ID) {
        let connected = false;

        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.error('SockJS/Stomp 라이브러리를 불러오지 못했습니다.');
            setStatus('채팅 모듈을 불러오지 못했습니다. 페이지를 새로고침 해주세요.', true);
        } else {
            const socket = new SockJS('/ws');
            const stomp = Stomp.over(socket);
            stomp.debug = null;

            setStatus('연결 중...', false);

            stomp.connect({}, () => {
                connected = true;
                setStatus('', false);
                stomp.subscribe(`/topic/chat/${ROOM_ID}`, (message) => {
                    appendMessage(JSON.parse(message.body));
                });
            }, (error) => {
                connected = false;
                console.error('STOMP 연결 실패:', error);
                setStatus('채팅 서버와 연결하지 못했습니다. 새로고침 후 다시 시도해주세요.', true);
            });

            document.getElementById('btn-chat-send').addEventListener('click', () => sendMessage(stomp, () => connected));
            document.getElementById('chat-input').addEventListener('keydown', e => {
                if (e.key === 'Enter') sendMessage(stomp, () => connected);
            });
        }

        function sendMessage(stomp, isConnected) {
            const input = document.getElementById('chat-input');
            const content = input.value.trim();
            if (!content) return;
            if (!isConnected()) {
                alert('채팅 서버 연결이 아직 안 되었습니다. 잠시 후 다시 시도해주세요.');
                return;
            }
            try {
                stomp.send(`/app/chat.send/${ROOM_ID}`, {}, JSON.stringify({ content: content }));
                input.value = '';
            } catch (e) {
                console.error('메시지 전송 실패:', e);
                alert('메시지 전송에 실패했습니다.');
            }
        }

        function setStatus(text, isError) {
            let el = document.getElementById('chat-status');
            if (!el) {
                el = document.createElement('div');
                el.id = 'chat-status';
                el.style.cssText = 'padding:6px 12px; font-size:11.5px; text-align:center;';
                document.getElementById('chat-log').parentElement.insertBefore(el, document.getElementById('chat-log'));
            }
            el.textContent = text;
            el.style.color = isError ? 'var(--danger)' : 'var(--ink-soft)';
            el.style.display = text ? 'block' : 'none';
        }

        function appendMessage(m) {
            const log = document.getElementById('chat-log');
            const div = document.createElement('div');
            div.className = 'chat-msg';
            div.dataset.original = m.content;
            div.innerHTML = `<b style="font-size:11.5px;">${escapeHtml(m.senderName)}</b>
                <span style="font-size:11px;color:var(--ink-soft);">${m.createdAt}</span>
                <div class="chat-content">${escapeHtml(m.content)}</div>
                <button type="button" class="btn translate-btn" style="font-size:10px;padding:2px 8px;margin-top:2px;">번역</button>`;
            log.appendChild(div);
            log.scrollTop = log.scrollHeight;
        }
    }

    // 번역 버튼 (이벤트 위임 - 처음부터 있던 메시지 + 새로 온 메시지 모두 처리)
    document.getElementById('chat-log')?.addEventListener('click', async (e) => {
        if (!e.target.classList.contains('translate-btn')) return;
        const wrap = e.target.closest('.chat-msg');
        const contentEl = wrap.querySelector('.chat-content');
        const isTranslated = e.target.dataset.translated === '1';

        if (isTranslated) {
            contentEl.textContent = wrap.dataset.original;
            e.target.textContent = '번역';
            e.target.dataset.translated = '0';
            return;
        }
        
        try {
            const result = await window.api.post('/api/chat/translate', { text: wrap.dataset.original, targetLang: MY_LANG });
            if (result.success) {
                contentEl.textContent = result.data;
                e.target.textContent = '원문';
                e.target.dataset.translated = '1';
            } else { alert(result.message || '번역에 실패했습니다.'); }
        } catch (err) {
            console.error('번역 실패:', err);
        }
    });

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    // ---------------- 투표 ----------------
    if (SCHEDULE_ID) {
        document.getElementById('btn-vote-agree')?.addEventListener('click', () => vote('agree'));
        document.getElementById('btn-vote-disagree')?.addEventListener('click', () => vote('disagree'));
    }
    async function vote(type) {
        const result = await window.api.post(`/api/planner/${SCHEDULE_ID}/vote?type=${type}`, {});
        if (!result.success) { alert(result.message); return; }
        const tally = await window.api.get(`/api/planner/${SCHEDULE_ID}/vote/tally`);
        if (tally.success) {
            document.getElementById('vote-tally').textContent = `찬성 ${tally.data.agree} · 반대 ${tally.data.disagree}`;
        }
    }

    // ---------------- 신청서 승인/거절 ----------------
    document.querySelectorAll('.approve-btn').forEach(btn => btn.addEventListener('click', () => review(btn, 'approve')));
    document.querySelectorAll('.reject-btn').forEach(btn => btn.addEventListener('click', () => review(btn, 'reject')));

    async function review(btn, action) {
        const appId = btn.dataset.appId;
        const result = await window.api.post(`/api/parties/${PARTY_ID}/applications/${appId}/${action}`, {});
        alert(result.message);
        if (result.success) location.reload();
    }

    // ---------------- 파티 나가기 ----------------
    document.getElementById('btn-leave-party')?.addEventListener('click', async function () {
        if (!confirm('정말 파티에서 나가시겠어요? 다시 들어오려면 재신청이 필요합니다.')) return;
        const result = await window.api.post(`/api/parties/${this.dataset.partyId}/leave`, {});
        alert(result.message);
        if (result.success) window.location.href = '/my-parties';
    });

    // ---------------- 강퇴 (방장 전용) ----------------
    document.querySelectorAll('.btn-kick').forEach(btn => {
        btn.addEventListener('click', async () => {
            if (!confirm(`정말 '${btn.dataset.userName}'님을 강퇴하시겠어요?`)) return;
            const result = await window.api.post(`/api/parties/${PARTY_ID}/members/${btn.dataset.userId}/kick`, {});
            alert(result.message);
            if (result.success) location.reload();
        });
    });
})();
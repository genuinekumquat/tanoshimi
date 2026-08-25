/**
 * 개인 간(1:1) 쪽지 채팅.
 * 파티 채팅과 완전히 같은 파이프(/topic/chat/{roomId}, /app/chat.send/{roomId})를 그대로 사용한다 -
 * chat_rooms 테이블이 type=party/dm 로 묶여 구조가 같기 때문.
 */
(function () {
    let connected = false;

    if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
        console.error('SockJS/Stomp 라이브러리를 불러오지 못했습니다.');
        setStatus('채팅 모듈을 불러오지 못했습니다. 페이지를 새로고침 해주세요.', true);
        return;
    }

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

    document.getElementById('btn-chat-send').addEventListener('click', sendMessage);
    document.getElementById('chat-input').addEventListener('keydown', e => { if (e.key === 'Enter') sendMessage(); });

    function sendMessage() {
        const input = document.getElementById('chat-input');
        const content = input.value.trim();
        if (!content) return;
        if (!connected) {
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

    // 번역 버튼 (이벤트 위임)
    document.getElementById('chat-log').addEventListener('click', async (e) => {
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

    const log = document.getElementById('chat-log');
    log.scrollTop = log.scrollHeight;
})();
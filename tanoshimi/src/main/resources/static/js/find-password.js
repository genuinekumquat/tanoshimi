(function () {
    const emailInput = document.getElementById('email');
    const codeInput = document.getElementById('code');
    const sendBtn = document.getElementById('btn-send-code');
    const submitBtn = document.getElementById('btn-find-password');

    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'msg ' + (ok ? 'success' : 'error');
    }

    function currentEmail() {
        return emailInput.value.trim().toLowerCase();
    }

    // 1단계: 메일함 소유 확인용 인증번호 발송
    sendBtn.addEventListener('click', async () => {
        const email = currentEmail();
        if (!email) { setMsg('msg-email', '이메일을 입력해 주세요.', false); return; }

        sendBtn.disabled = true;
        const result = await window.api.post('/api/auth/find-password/send', { email });
        setMsg('msg-email', result.message, result.success);
        sendBtn.disabled = false;
        if (result.success) codeInput.focus();
    });

    // 2단계: 인증번호가 맞으면 임시 비밀번호 발급
    submitBtn.addEventListener('click', async () => {
        const email = currentEmail();
        const code = codeInput.value.trim();
        if (!email) { setMsg('msg-email', '이메일을 입력해 주세요.', false); return; }
        if (!/^[0-9]{6}$/.test(code)) { setMsg('msg-code', '이메일로 받은 6자리 인증번호를 입력해 주세요.', false); return; }

        submitBtn.disabled = true;
        const result = await window.api.post('/api/auth/find-password', { email, code });
        setMsg('msg-code', result.message, result.success);
        submitBtn.disabled = false;
    });
})();

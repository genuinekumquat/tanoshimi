(function () {
    const emailInput = document.getElementById('email');
    const submitBtn = document.getElementById('btn-find-password');

    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'msg ' + (ok ? 'success' : 'error');
    }

    submitBtn.addEventListener('click', async () => {
        const email = emailInput.value.trim().toLowerCase();
        if (!email) { setMsg('msg-email', '이메일을 입력해 주세요.', false); return; }

        submitBtn.disabled = true;
        const result = await window.api.post('/api/auth/find-password', { email });
        setMsg('msg-email', result.message, result.success);
        submitBtn.disabled = false;
    });
})();

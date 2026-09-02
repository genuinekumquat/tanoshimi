(function () {
    const nameInput = document.getElementById('name');
    const genderSelect = document.getElementById('gender');
    const nationalitySelect = document.getElementById('nationality');
    const birthDateInput = document.getElementById('birthDate');
    const phoneInput = document.getElementById('phone');
    const emailInput = document.getElementById('email');
    const codeField = document.getElementById('code-field');
    const codeInput = document.getElementById('code');
    const timerEl = document.getElementById('timer');
    const termsInput = document.getElementById('termsAgreed');
    const submitBtn = document.getElementById('btn-submit');

    let emailVerified = false;
    let timerId = null;

    function onlyDigits(v) { return (v || '').replace(/[^0-9]/g, ''); }
    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'msg ' + (ok ? 'success' : 'error');
    }

    function refreshSubmit() {
        submitBtn.disabled = !(nameInput.value.trim().length >= 2 && genderSelect.value
            && nationalitySelect.value && birthDateInput.value
            && onlyDigits(phoneInput.value).length >= 10 && emailVerified && termsInput.checked);
    }
    [nameInput, genderSelect, nationalitySelect, birthDateInput, phoneInput, termsInput].forEach(el => {
        el.addEventListener('input', refreshSubmit); el.addEventListener('change', refreshSubmit);
    });

    document.getElementById('btn-send-code').addEventListener('click', async () => {
        const email = emailInput.value.trim().toLowerCase();
        const result = await window.api.post('/api/verification/email/send', { email });
        setMsg('msg-email-verify', result.message, result.success);
        if (!result.success) return;
        codeField.style.display = 'block';
        startTimer(300);
    });

    document.getElementById('btn-confirm-code').addEventListener('click', async () => {
        const email = emailInput.value.trim().toLowerCase();
        const result = await window.api.post('/api/verification/email/confirm', { email, code: codeInput.value.trim() });
        setMsg('msg-code', result.message, result.success);
        if (result.success) { emailVerified = true; stopTimer(); refreshSubmit(); }
    });

    function startTimer(seconds) {
        stopTimer();
        let remain = seconds;
        render();
        timerId = setInterval(() => { remain--; render(); if (remain <= 0) stopTimer(); }, 1000);
        function render() {
            const m = String(Math.floor(Math.max(remain,0)/60)).padStart(2,'0');
            const s = String(Math.max(remain,0)%60).padStart(2,'0');
            timerEl.textContent = m + ':' + s;
        }
    }
    function stopTimer() { if (timerId) clearInterval(timerId); timerId = null; }

    submitBtn.addEventListener('click', async () => {
        submitBtn.disabled = true;
        const result = await window.api.post('/api/auth/signup/social', {
            name: nameInput.value.trim(),
            email: emailInput ? emailInput.value.trim() : null,
            phone: onlyDigits(phoneInput.value),
            gender: genderSelect.value,
            birthDate: birthDateInput.value,
            nationality: nationalitySelect.value,
            termsAgreed: termsInput.checked
        });
        if (!result.success) {
            document.getElementById('msg-form').textContent = result.message || '가입에 실패했습니다.';
            submitBtn.disabled = false;
            return;
        }
        window.location.href = '/';
    });
})();

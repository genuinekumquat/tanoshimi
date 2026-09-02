(function () {
    const emailInput = document.getElementById('email');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const passwordConfirmInput = document.getElementById('passwordConfirm');
    const nameInput = document.getElementById('name');
    const genderSelect = document.getElementById('gender');
    const nationalitySelect = document.getElementById('nationality');
    const birthDateInput = document.getElementById('birthDate');
    const phoneInput = document.getElementById('phone');
    const codeField = document.getElementById('code-field');
    const codeInput = document.getElementById('code');
    const timerEl = document.getElementById('timer');
    const termsInput = document.getElementById('termsAgreed');
    const signupBtn = document.getElementById('btn-signup');
    const formMsg = document.getElementById('msg-form');

    let emailChecked = false;
    let usernameChecked = false;
    let phoneVerified = false;
    let timerId = null;

    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'msg ' + (ok ? 'success' : 'error');
    }

    function onlyDigits(v) { return (v || '').replace(/[^0-9]/g, ''); }

    function formatPhone(v) {
        const d = onlyDigits(v).slice(0, 11);
        if (d.length < 4) return d;
        if (d.length < 8) return d.slice(0,3) + '-' + d.slice(3);
        return d.slice(0,3) + '-' + d.slice(3,7) + '-' + d.slice(7);
    }

    function validPassword(v) {
        if (!v || v.length < 8 || v.length > 20 || /\s/.test(v)) return false;
        const kinds = [/[A-Za-z]/, /[0-9]/, /[!@#$%^&*()_+\-=\[\]{}|;:',.<>/?~`"\\]/].filter(re => re.test(v)).length;
        return kinds >= 2;
    }

    function refreshSubmit() {
        signupBtn.disabled = !(emailChecked && usernameChecked && validPassword(passwordInput.value)
            && passwordInput.value === passwordConfirmInput.value
            && nameInput.value.trim().length >= 2
            && genderSelect.value && nationalitySelect.value && birthDateInput.value
            && phoneVerified && termsInput.checked);
    }

    emailInput.addEventListener('input', () => { emailChecked = false; refreshSubmit(); });
    document.getElementById('btn-email-check').addEventListener('click', async () => {
        const email = emailInput.value.trim().toLowerCase();
        const result = await window.api.get('/api/auth/email-check?email=' + encodeURIComponent(email));
        emailChecked = !!result.data;
        setMsg('msg-email', result.message, emailChecked);
        refreshSubmit();
    });

    usernameInput.addEventListener('input', () => {
        usernameInput.value = usernameInput.value.toLowerCase();
        usernameChecked = false;
        refreshSubmit();
    });
    document.getElementById('btn-username-check').addEventListener('click', async () => {
        const username = usernameInput.value.trim().toLowerCase();
        if (!/^[a-z][a-z0-9_]{2,19}$/.test(username)) {
            usernameChecked = false;
            setMsg('msg-username', '영문 소문자로 시작하는 3~20자의 소문자/숫자/밑줄만 쓸 수 있어요.', false);
            refreshSubmit();
            return;
        }
        const result = await window.api.get('/api/auth/username-check?username=' + encodeURIComponent(username));
        usernameChecked = !!result.data;
        setMsg('msg-username', result.message, usernameChecked);
        refreshSubmit();
    });

    passwordInput.addEventListener('input', () => {
        const ok = validPassword(passwordInput.value);
        setMsg('msg-password', ok ? '사용 가능합니다.' : '8~20자, 영문/숫자/특수문자 중 2가지 이상', ok);
        refreshSubmit();
    });
    passwordConfirmInput.addEventListener('input', () => {
        const ok = passwordInput.value === passwordConfirmInput.value && passwordConfirmInput.value !== '';
        setMsg('msg-password-confirm', ok ? '일치합니다.' : '비밀번호가 일치하지 않습니다.', ok);
        refreshSubmit();
    });
    [nameInput, genderSelect, nationalitySelect, birthDateInput, termsInput].forEach(el =>
        el.addEventListener('input', refreshSubmit) || el.addEventListener('change', refreshSubmit));

    phoneInput.addEventListener('input', () => {
        phoneInput.value = formatPhone(phoneInput.value);
        phoneVerified = false;
        refreshSubmit();
    });

    document.getElementById('btn-send-code').addEventListener('click', async () => {
        const phone = onlyDigits(phoneInput.value);
        const result = await window.api.post('/api/verification/phone/send', { phone });
        setMsg('msg-phone', result.message, result.success);
        if (!result.success) return;
        codeField.style.display = 'block';
        startTimer(300);
    });

    document.getElementById('btn-confirm-code').addEventListener('click', async () => {
        const phone = onlyDigits(phoneInput.value);
        const code = codeInput.value.trim();
        const result = await window.api.post('/api/verification/phone/confirm', { phone, code });
        setMsg('msg-code', result.message, result.success);
        if (result.success) { phoneVerified = true; stopTimer(); refreshSubmit(); }
    });

    function startTimer(seconds) {
        stopTimer();
        let remain = seconds;
        render();
        timerId = setInterval(() => {
            remain--; render();
            if (remain <= 0) { stopTimer(); setMsg('msg-code', '인증시간이 만료되었습니다.', false); }
        }, 1000);
        function render() {
            const m = String(Math.floor(Math.max(remain,0)/60)).padStart(2,'0');
            const s = String(Math.max(remain,0)%60).padStart(2,'0');
            timerEl.textContent = m + ':' + s;
        }
    }
    function stopTimer() { if (timerId) clearInterval(timerId); timerId = null; }

    signupBtn.addEventListener('click', async () => {
        signupBtn.disabled = true;
        const result = await window.api.post('/api/auth/signup', {
            email: emailInput.value.trim().toLowerCase(),
            username: usernameInput.value.trim().toLowerCase(),
            password: passwordInput.value,
            passwordConfirm: passwordConfirmInput.value,
            name: nameInput.value.trim(),
            phone: onlyDigits(phoneInput.value),
            gender: genderSelect.value,
            birthDate: birthDateInput.value,
            nationality: nationalitySelect.value,
            termsAgreed: termsInput.checked
        });
        if (!result.success) {
            formMsg.textContent = result.message || '가입에 실패했습니다.';
            signupBtn.disabled = false;
            return;
        }
        window.location.href = '/signup/complete';
    });
})();

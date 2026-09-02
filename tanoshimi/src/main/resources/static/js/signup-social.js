(function () {
    const nameInput = document.getElementById('name');
    const usernameInput = document.getElementById('username');
    const genderSelect = document.getElementById('gender');
    const nationalitySelect = document.getElementById('nationality');
    const birthDateInput = document.getElementById('birthDate');
    const phoneInput = document.getElementById('phone');
    const emailInput = document.getElementById('email'); // 있을 수도, 없을 수도(needEmailInput=false 면 disabled)
    const termsInput = document.getElementById('termsAgreed');
    const submitBtn = document.getElementById('btn-submit');

    let usernameChecked = false;

    function onlyDigits(v) { return (v || '').replace(/[^0-9]/g, ''); }
    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'msg ' + (ok ? 'success' : 'error');
    }

    function refreshSubmit() {
        submitBtn.disabled = !(nameInput.value.trim().length >= 2 && usernameChecked && genderSelect.value
            && nationalitySelect.value && birthDateInput.value
            && onlyDigits(phoneInput.value).length >= 10 && termsInput.checked);
    }
    [nameInput, genderSelect, nationalitySelect, birthDateInput, phoneInput, termsInput].forEach(el => {
        el.addEventListener('input', refreshSubmit); el.addEventListener('change', refreshSubmit);
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

    refreshSubmit();

    submitBtn.addEventListener('click', async () => {
        submitBtn.disabled = true;
        const result = await window.api.post('/api/auth/signup/social', {
            name: nameInput.value.trim(),
            username: usernameInput.value.trim().toLowerCase(),
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

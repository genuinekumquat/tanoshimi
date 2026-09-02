(function () {
    const currentInput = document.getElementById('fp-current');
    const newInput = document.getElementById('fp-new');
    const confirmInput = document.getElementById('fp-confirm');
    const submitBtn = document.getElementById('fp-submit');
    const formMsg = document.getElementById('fp-msg-form');
    if (!submitBtn) return;

    function setMsg(id, text, ok) {
        const el = document.getElementById(id);
        el.textContent = text;
        el.className = 'fp-msg ' + (ok ? 'success' : 'error');
    }

    function validPassword(v) {
        if (!v || v.length < 8 || v.length > 20 || /\s/.test(v)) return false;
        const kinds = [/[A-Za-z]/, /[0-9]/, /[!@#$%^&*()_+\-=\[\]{}|;:',.<>/?~`"\\]/].filter(re => re.test(v)).length;
        return kinds >= 2;
    }

    newInput.addEventListener('input', () => {
        const ok = validPassword(newInput.value);
        setMsg('fp-msg-new', ok ? '사용 가능합니다.' : '8~20자, 영문/숫자/특수문자 중 2가지 이상', ok);
    });
    confirmInput.addEventListener('input', () => {
        const ok = newInput.value === confirmInput.value && confirmInput.value !== '';
        setMsg('fp-msg-confirm', ok ? '일치합니다.' : '비밀번호가 일치하지 않습니다.', ok);
    });

    submitBtn.addEventListener('click', async () => {
        formMsg.textContent = '';
        if (!validPassword(newInput.value)) {
            setMsg('fp-msg-new', '8~20자, 영문/숫자/특수문자 중 2가지 이상', false);
            return;
        }
        if (newInput.value !== confirmInput.value) {
            setMsg('fp-msg-confirm', '비밀번호가 일치하지 않습니다.', false);
            return;
        }

        submitBtn.disabled = true;
        const result = await window.api.post('/api/mypage/change-password', {
            currentPassword: currentInput.value,
            newPassword: newInput.value,
            newPasswordConfirm: confirmInput.value
        });
        if (!result.success) {
            formMsg.textContent = result.message || '비밀번호 변경에 실패했습니다.';
            submitBtn.disabled = false;
            return;
        }
        window.location.reload();
    });
})();

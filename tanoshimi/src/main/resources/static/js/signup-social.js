(function () {
    const nameInput = document.getElementById('name');
    const genderSelect = document.getElementById('gender');
    const nationalitySelect = document.getElementById('nationality');
    const birthDateInput = document.getElementById('birthDate');
    const phoneInput = document.getElementById('phone');
    const emailInput = document.getElementById('email');
    const termsInput = document.getElementById('termsAgreed');
    const submitBtn = document.getElementById('btn-submit');

    function onlyDigits(v) { return (v || '').replace(/[^0-9]/g, ''); }

    function refreshSubmit() {
        submitBtn.disabled = !(nameInput.value.trim().length >= 2 && genderSelect.value
            && nationalitySelect.value && birthDateInput.value
            && onlyDigits(phoneInput.value).length >= 10 && termsInput.checked);
    }
    [nameInput, genderSelect, nationalitySelect, birthDateInput, phoneInput, termsInput].forEach(el => {
        el.addEventListener('input', refreshSubmit); el.addEventListener('change', refreshSubmit);
    });
    refreshSubmit();

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

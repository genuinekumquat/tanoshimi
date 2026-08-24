(function () {
    const btn = document.getElementById('btn-apply');
    if (!btn) return;
    btn.addEventListener('click', async () => {
        const message = document.getElementById('apply-message').value.trim();
        if (!message) { alert('간단한 메시지를 입력해 주세요.'); return; }
        const partyId = btn.dataset.partyId;
        const result = await window.api.post(`/api/parties/${partyId}/apply`, { message });
        alert(result.message);
        if (result.success) btn.disabled = true;
    });
})();

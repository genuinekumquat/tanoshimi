/** 마이페이지 아바타 클릭 -> 파일 선택 -> 즉시 업로드(multipart). */
(function () {
    const wrap = document.getElementById('avatar-wrap');
    const input = document.getElementById('avatar-input');
    if (!wrap || !input) return;

    wrap.addEventListener('click', () => input.click());

    input.addEventListener('change', async () => {
        const file = input.files[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) { alert('5MB 이하 이미지만 업로드할 수 있습니다.'); return; }

        const formData = new FormData();
        formData.append('file', file);

        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = {};
        if (tokenMeta && headerMeta) headers[headerMeta.content] = tokenMeta.content;

        try {
            const response = await fetch('/api/mypage/profile-image', { method: 'POST', body: formData, headers });
            const result = await response.json();
            if (!result.success) { alert(result.message || '업로드에 실패했습니다.'); return; }
            location.reload();
        } catch (e) {
            alert('업로드 중 오류가 발생했습니다.');
        }
    });
})();

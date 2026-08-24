/**
 * 게시판/마이페이지/파티 전용 게시판 공용 글쓰기 모달 - 같은 API(/api/posts)를 호출한다.
 * 사진을 실제로 올리면 /api/uploads/image 로 먼저 업로드하고, 그 결과 URL을 썸네일로 쓴다.
 * 파티 게시판에서 쓸 때는 이 스크립트가 실행되기 전에 전역변수 CURRENT_PARTY_ID 를 정의해두면
 * 그 파티에 연결된 글로 등록된다(없으면 게시판/마이페이지 공용 글).
 */
(function () {
    const btnWrite = document.getElementById('btn-write');
    const modal = document.getElementById('write-modal');
    if (!btnWrite || !modal) return;

    const partyId = (typeof CURRENT_PARTY_ID !== 'undefined') ? CURRENT_PARTY_ID : null;

    btnWrite.addEventListener('click', () => { modal.style.display = 'flex'; });
    document.getElementById('btn-write-cancel').addEventListener('click', () => { modal.style.display = 'none'; });

    const fileInput = document.getElementById('w-thumbnail');
    const preview = document.getElementById('w-thumbnail-preview');
    fileInput?.addEventListener('change', () => {
        const file = fileInput.files[0];
        if (!file) { preview.style.display = 'none'; return; }
        preview.src = URL.createObjectURL(file);
        preview.style.display = 'block';
    });

    document.getElementById('btn-write-submit').addEventListener('click', async () => {
        const title = document.getElementById('w-title').value.trim();
        const content = document.getElementById('w-content').value.trim();
        const region = document.getElementById('w-region').value.trim();
        if (!title || !content) { alert('제목과 내용을 입력해 주세요.'); return; }

        const btn = document.getElementById('btn-write-submit');
        btn.disabled = true;

        let thumbnailUrl;
        const file = fileInput?.files[0];
        if (file) {
            thumbnailUrl = await uploadImage(file);
            if (!thumbnailUrl) { btn.disabled = false; return; } // uploadImage 가 이미 알림을 띄웠음
        } else {
            const thumbs = ['ph1', 'ph2', 'ph3', 'ph4'];
            thumbnailUrl = thumbs[Math.floor(Math.random() * thumbs.length)];
        }

        const result = await window.api.post('/api/posts', { title, content, region: region || null, thumbnailUrl, partyId });
        btn.disabled = false;
        if (!result.success) { alert(result.message); return; }
        location.reload();
    });

    async function uploadImage(file) {
        const formData = new FormData();
        formData.append('file', file);
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = {};
        if (tokenMeta && headerMeta) headers[headerMeta.content] = tokenMeta.content;

        try {
            const response = await fetch('/api/uploads/image', { method: 'POST', body: formData, headers });
            const result = await response.json();
            if (!result.success) { alert(result.message || '사진 업로드에 실패했습니다.'); return null; }
            return result.data;
        } catch (e) {
            console.error('업로드 오류:', e);
            alert('사진 업로드 중 오류가 발생했습니다.');
            return null;
        }
    }
})();

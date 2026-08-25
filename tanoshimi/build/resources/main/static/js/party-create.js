(function () {
    const btn = document.getElementById('btn-create');
    const msg = document.getElementById('msg-form');

    const fileInput = document.getElementById('thumbnail');
    const preview = document.getElementById('thumbnail-preview');
    fileInput?.addEventListener('change', () => {
        const file = fileInput.files[0];
        if (!file) { preview.style.display = 'none'; return; }
        preview.src = URL.createObjectURL(file);
        preview.style.display = 'block';
    });

    btn.addEventListener('click', async () => {
        const title = document.getElementById('title').value.trim();
        const region = document.getElementById('region').value;
        const departureDate = document.getElementById('departureDate').value;
        const capacity = parseInt(document.getElementById('capacity').value, 10) || 0;

        if (!title || !region || !departureDate || capacity < 1) {
            msg.textContent = '제목, 지역, 출발일, 정원은 필수입니다.';
            return;
        }

        btn.disabled = true;

        let thumbnailUrl;
        const file = fileInput.files[0];
        if (file) {
            thumbnailUrl = await uploadImage(file);
            if (!thumbnailUrl) { btn.disabled = false; return; }
        } else {
            const thumbs = ['ph1', 'ph2', 'ph3', 'ph4'];
            thumbnailUrl = thumbs[Math.floor(Math.random() * thumbs.length)];
        }

        const budgetKrwVal = document.getElementById('budgetKrw').value;
        const ageMinVal = document.getElementById('ageMin').value;
        const ageMaxVal = document.getElementById('ageMax').value;
        const tourIdVal = document.getElementById('tourId').value;

        const result = await window.api.post('/api/parties', {
            title,
            description: document.getElementById('description').value.trim(),
            region,
            departureDate,
            budgetKrw: budgetKrwVal ? parseInt(budgetKrwVal, 10) : null,
            capacity,
            styleTag: document.getElementById('styleTag').value || null,
            genderRestriction: document.getElementById('genderRestriction').value,
            ageMin: ageMinVal ? parseInt(ageMinVal, 10) : null,
            ageMax: ageMaxVal ? parseInt(ageMaxVal, 10) : null,
            nationalityRestriction: document.getElementById('nationalityRestriction').value,
            thumbnailUrl,
            tourId: tourIdVal ? parseInt(tourIdVal, 10) : null
        });
        btn.disabled = false;

        if (!result.success) { msg.textContent = result.message || '파티 생성에 실패했습니다.'; return; }
        window.location.href = `/party-board/${result.data}/room`;
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
            if (!result.success) { msg.textContent = result.message || '사진 업로드에 실패했습니다.'; return null; }
            return result.data;
        } catch (e) {
            console.error('업로드 오류:', e);
            msg.textContent = '사진 업로드 중 오류가 발생했습니다.';
            return null;
        }
    }
})();

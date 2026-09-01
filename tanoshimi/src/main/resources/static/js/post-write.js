/**
 * 게시판/마이페이지/파티 전용 게시판 공용 글쓰기 모달 - 같은 API(/api/posts)를 호출한다.
 * 사진을 실제로 올리면 /api/uploads/image 로 먼저 업로드하고, 그 결과 URL을 썸네일로 쓴다.
 * 파티 게시판에서 쓸 때는 이 스크립트가 실행되기 전에 전역변수 CURRENT_PARTY_ID 를 정의해두면
 * 그 파티에 연결된 글로 등록된다(없으면 게시판/마이페이지 공용 글).
 *
 * [v20-4] #btn-write(페이지 전역 "글쓰기" 버튼)는 선택 사항이다 - 없어도 이 모달의 나머지
 * 로직(제출, 취소, 여행 선택 등)은 그대로 동작한다. 대신 window.openWriteModalWithTrip(tripId)
 * 를 전역에 노출해서, 다른 스크립트가 특정 여행을 미리 골라둔 채로 이 모달을 열 수 있게
 * 했다 - /mypage/mytrip의 "스냅 업로드" 버튼(mypage-trips.js)이 이 방식을 쓴다.
 */
(function () {
    // [v20-4] #btn-write는 이제 선택이다 - mypage/mytrip.html처럼 페이지 전역 글쓰기 버튼
    // 없이, 여행 카드별 "스냅 업로드" 버튼(mypage-trips.js)이 window.openWriteModalWithTrip
    // 으로 이 모달을 직접 여는 페이지도 있기 때문이다. #write-modal 자체는 여전히 필수.
    const btnWrite = document.getElementById('btn-write');
    const modal = document.getElementById('write-modal');
    if (!modal) return;

    const partyId = (typeof CURRENT_PARTY_ID !== 'undefined') ? CURRENT_PARTY_ID : null;

    btnWrite?.addEventListener('click', () => { modal.style.display = 'flex'; });
    document.getElementById('btn-write-cancel').addEventListener('click', () => { modal.style.display = 'none'; });

    const fileInput = document.getElementById('w-thumbnail');
    const preview = document.getElementById('w-thumbnail-preview');
    fileInput?.addEventListener('change', () => {
        const file = fileInput.files[0];
        if (!file) { preview.style.display = 'none'; return; }
        preview.src = URL.createObjectURL(file);
        preview.style.display = 'block';
    });

    // [v19 신규] "내 여행" 중 하나를 고르면 지역을 그 여행의 여행지로 자동 채우고 잠근다
    // (마이페이지에만 있는 select라 board/party 글쓰기에서는 그냥 없다 - w-trip 이 없으면
    // 아래는 전부 조용히 건너뛴다). 여행 횟수 집계에는 관여하지 않는다 - my_trips 자체가
    // 근거라 이 선택은 순수하게 "이 사진이 어느 여행 기록인지" 표시용이다.
    const tripSelect = document.getElementById('w-trip');
    const regionInput = document.getElementById('w-region');
    tripSelect?.addEventListener('change', () => {
        const opt = tripSelect.selectedOptions[0];
        if (tripSelect.value && opt) {
            regionInput.value = opt.dataset.destination || '';
            regionInput.readOnly = true;
            regionInput.placeholder = '선택한 여행의 여행지로 자동 입력됨';
        } else {
            regionInput.readOnly = false;
            regionInput.placeholder = '지역(선택)';
        }
    });

    // [v20-4 신규] 다른 스크립트가 특정 "내 여행"을 미리 선택한 채로 이 모달을 열 수 있게
    // 해주는 진입점 - "내 여행" 관리 화면(/mypage/mytrip)의 "📸 스냅 업로드" 버튼이 쓴다
    // (mypage-trips.js). "여행을 먼저 등록하고, 스냅을 올릴 때 그 여행을 고른다"는 정방향
    // 순서를 그대로 재사용하는 것뿐이라 서버 API나 write() 로직은 전혀 안 건드린다.
    window.openWriteModalWithTrip = function (tripId) {
        modal.style.display = 'flex';
        if (tripSelect && tripId != null) {
            tripSelect.value = String(tripId);
            tripSelect.dispatchEvent(new Event('change'));
        }
    };

    document.getElementById('btn-write-submit').addEventListener('click', async () => {
        const title = document.getElementById('w-title').value.trim();
        const content = document.getElementById('w-content').value.trim();
        const region = document.getElementById('w-region').value.trim();
        const tripId = tripSelect?.value ? Number(tripSelect.value) : null;
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

        const result = await window.api.post('/api/posts', { title, content, region: region || null, thumbnailUrl, partyId, tripId });
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

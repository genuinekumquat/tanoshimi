/**
 * "내 여행" 관리 페이지(/mypage/mytrip) 전용 - 카드 렌더링 + 추가/수정/삭제 + 스냅 업로드
 * 진입점. 담당: 김민규(⑥). v19 신규, [v20] 별도 페이지로 이동.
 *
 * 여행 목록은 서버(MyPageController)가 #trips-data 에 data-* 로 내려준 것을 읽어 그린다
 * (다른 섹션들 - titles-data 등 - 과 같은 방식, 엔티티를 통째로 JS로 직렬화하지 않는다).
 * 추가/수정/삭제는 /api/my-trips 를 호출하고, 성공하면 페이지를 새로고침한다 - 여행 목록
 * 자체가 지도 색·"N번의 여행"·칭호 판정 근거라 여러 군데를 부분 갱신하는 것보다 서버가
 * 다시 계산한 값을 통째로 받는 쪽이 안전하다(post-write.js 의 글쓰기 성공 후
 * location.reload() 와 같은 방식).
 *
 * [v20-4] "스냅 인증 대기"(파티 완료로 자동 등록됐지만 아직 연결된 스냅이 없는) 여행 카드의
 * "📸 스냅 업로드" 버튼은 새 스냅을 이 여행으로 바로 올릴 수 있게 글쓰기 모달을 그 여행이
 * 미리 선택된 채로 연다(post-write.js 의 window.openWriteModalWithTrip). 예전에 이미 올려둔
 * 스냅을 소급 연결해주던 "스냅 연결" 기능(v20-2)은 "구제하지 말고, 여행을 먼저 등록하고
 * 스냅을 올릴 때 그 여행을 선택하는 정방향 순서만 인정하라"는 요청으로 걷어냈다 - 그
 * 정방향 경로(글쓰기 모달 "여행 선택")는 v19-1부터 이미 있던 것을 그대로 재사용한다.
 *
 * [v20-7] 직접 등록(SOLO) 카드에도 "✍️ 직접 등록" 배지를 붙이고(파티 자동 등록 배지와
 * 대비), 모든 카드(파티/SOLO, 카운트 여부 무관)에 "📷 스냅 보기" 버튼을 달아 이 여행에
 * 연결된 스냅을 읽기 전용 모달로 볼 수 있게 한다(GET /api/my-trips/{id}/snaps).
 */
(function () {
    'use strict';

    var listEl = document.getElementById('trip-list');
    var dataEl = document.getElementById('trips-data');
    if (!listEl || !dataEl) return; // 이 섹션이 없는 페이지에서는 아무것도 하지 않는다.

    var modal = document.getElementById('trip-modal');
    var modalTitle = document.getElementById('trip-modal-title');
    var fId = document.getElementById('trip-id');
    var fTitle = document.getElementById('trip-title');
    var fDest = document.getElementById('trip-destination');
    var fStart = document.getElementById('trip-start');
    var fEnd = document.getElementById('trip-end');
    var fMemo = document.getElementById('trip-memo');
    var daysPreview = document.getElementById('trip-days-preview');
    var btnAdd = document.getElementById('btn-trip-add');
    var btnCancel = document.getElementById('trip-cancel');
    var btnSave = document.getElementById('trip-save');

    // [v20-7 신규] "📷 스냅 보기" 읽기 전용 모달 요소들.
    var snapViewModal = document.getElementById('snap-view-modal');
    var snapViewTitle = document.getElementById('snap-view-title');
    var snapViewSub = document.getElementById('snap-view-sub');
    var snapViewGrid = document.getElementById('snap-view-grid');
    var snapViewEmpty = document.getElementById('snap-view-empty');
    var btnSnapViewClose = document.getElementById('snap-view-close');

    function trips() {
        return [].map.call(dataEl.querySelectorAll('span'), function (el) {
            return {
                id: el.dataset.id,
                source: el.dataset.source,
                title: el.dataset.title,
                destination: el.dataset.destination,
                start: el.dataset.start,
                end: el.dataset.end,
                days: parseInt(el.dataset.days, 10) || 1,
                memo: el.dataset.memo || '',
                manageable: el.dataset.manageable === 'true',
                // [v19-4 신규] 파티 여행인데 아직 연결된 스냅이 없으면 false - 여행 횟수/
                // 지도/칭호에는 아직 반영되지 않고 있다는 뜻(MyTripView.counted 참고).
                counted: el.dataset.counted === 'true'
            };
        });
    }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    function nightsLabel(days) {
        return days <= 1 ? '당일치기' : (days - 1) + '박 ' + days + '일';
    }

    function cardHtml(t) {
        // [v19-4 신규] PARTY 여행 중에서도 스냅이 아직 없으면(counted=false) "인증 대기"로
        // 다르게 표시한다 - 파티만 완료 처리하고 실제로 안 간 여행은 카운트되지 않기 때문에,
        // 사용자가 이유를 보고 스냅을 올리도록 안내한다.
        // [v20-7] 직접 등록(SOLO) 카드에도 대비되는 배지를 붙인다 - 이전에는 SOLO 카드만
        // 아무 배지 없이 밋밋했다.
        var badge = t.manageable
            ? '<span class="trip-badge manual">✍️ 직접 등록</span>'
            : (t.counted
                ? '<span class="trip-badge">🎒 파티 완료로 자동 등록</span>'
                : '<span class="trip-badge pending">📸 스냅 인증 대기 (사진을 올리면 반영돼요)</span>');
        // [v20-7 신규] 모든 카드에 "📷 스냅 보기" 버튼을 단다(파티/SOLO, 카운트 여부 무관) -
        // 스냅 인증 대기 상태여도 눌러보면 "아직 스냅이 없어요"가 정상 응답이라 굳이 숨기지
        // 않는다. data-title 은 모달 제목에 쓴다(esc 로 이스케이프해서 속성에 넣음).
        var viewBtn = '<button type="button" class="trip-view-snaps" data-id="' + t.id +
            '" data-title="' + esc(t.title) + '">📷 스냅 보기</button>';
        // [v20-4] 아직 카운트가 안 된 PARTY 여행("스냅 인증 대기")에는 "스냅 업로드" 버튼을
        // 달아준다 - 클릭하면 글쓰기 모달이 이 여행을 미리 선택한 채로 열린다(새 스냅을
        // 바로 올리는 정방향 경로 - openWriteModalWithTrip 참고). 이미 있는 스냅을 골라
        // 연결해주던 버튼(v20-2)은 없앴다.
        var actions;
        if (t.manageable) {
            actions = '<div class="trip-actions"><div class="trip-actions-row">' +
              '<button type="button" class="trip-edit" data-id="' + t.id + '">수정</button>' +
              '<button type="button" class="trip-del" data-id="' + t.id + '">삭제</button>' +
              '</div><div class="trip-actions-row">' + viewBtn + '</div></div>';
        } else if (!t.counted) {
            actions = '<div class="trip-actions"><div class="trip-actions-row">' +
              '<button type="button" class="trip-upload-snap" data-id="' + t.id + '">📸 스냅 업로드</button>' +
              '</div><div class="trip-actions-row">' + viewBtn + '</div></div>';
        } else {
            actions = '<div class="trip-actions"><div class="trip-actions-row">' + viewBtn + '</div></div>';
        }
        var memo = t.memo ? '<div class="trip-memo">' + esc(t.memo) + '</div>' : '';
        return '<div class="trip-card">' +
            '<div class="trip-main">' +
            '<div class="trip-title">' + esc(t.title) + '</div>' +
            '<div class="trip-sub">📍 ' + esc(t.destination) + ' · ' + t.start + ' ~ ' + t.end + ' · ' + nightsLabel(t.days) + '</div>' +
            memo +
            '</div>' + badge + actions + '</div>';
    }

    function render() {
        var list = trips();
        listEl.innerHTML = list.map(cardHtml).join('');
        var empty = document.getElementById('trip-empty');
        if (empty) empty.style.display = list.length ? 'none' : '';
    }

    function updateDaysPreview() {
        if (!fStart.value || !fEnd.value) { daysPreview.textContent = ''; return; }
        var start = new Date(fStart.value), end = new Date(fEnd.value);
        var diff = Math.round((end - start) / 86400000);
        if (diff < 0) { daysPreview.textContent = '오는 날짜는 가는 날짜보다 빠를 수 없어요.'; return; }
        daysPreview.textContent = nightsLabel(diff + 1);
    }
    fStart.addEventListener('change', updateDaysPreview);
    fEnd.addEventListener('change', updateDaysPreview);

    function openModal(trip) {
        if (trip) {
            modalTitle.textContent = '여행 수정';
            fId.value = trip.id;
            fTitle.value = trip.title;
            fDest.value = trip.destination;
            fStart.value = trip.start;
            fEnd.value = trip.end;
            fMemo.value = trip.memo;
        } else {
            modalTitle.textContent = '여행 추가';
            fId.value = '';
            fTitle.value = ''; fDest.value = ''; fStart.value = ''; fEnd.value = ''; fMemo.value = '';
        }
        updateDaysPreview();
        modal.classList.add('show');
    }
    function closeModal() { modal.classList.remove('show'); }

    // [v20-7 신규] 스냅 카드 하나 - board/list.html·mypage/index.html 의 썸네일 규칙과
    // 동일하게 thumbnailUrl 이 '/uploads/'로 시작하면 실제 업로드 이미지, 아니면
    // 자리표시 클래스명(ph1~ph4, 없으면 ph1)으로 그린다.
    function snapCardHtml(s) {
        var thumb = s.thumbnailUrl || '';
        var uploaded = thumb.indexOf('/uploads/') === 0;
        var phClass = /^ph[1-4]$/.test(thumb) ? thumb : 'ph1';
        var phStyle = uploaded ? ' style="background-image:url(\'' + encodeURI(thumb) + '\');"' : '';
        return '<div class="snap-view-item">' +
            '<div class="ph' + (uploaded ? '' : ' ' + phClass) + '"' + phStyle + '></div>' +
            '<div class="snap-view-cap"><b>' + esc(s.title) + '</b>' +
            (s.region ? esc(s.region) + (s.createdDate ? ' · ' + s.createdDate : '') : (s.createdDate || '')) +
            '</div></div>';
    }

    // [v20-7 신규] 여행 하나에 연결된 스냅을 불러와 읽기 전용 모달로 보여준다.
    function openSnapViewModal(tripId, tripTitle) {
        if (!snapViewModal) return;
        snapViewTitle.textContent = '📷 스냅 보기';
        // textContent 에 넣을 값이라 이스케이프가 필요 없다(esc 는 innerHTML 용) - dataset에서
        // 읽은 tripTitle 은 이미 브라우저가 HTML 엔티티를 원문으로 복원해준 상태다.
        snapViewSub.textContent = (tripTitle || '') + '에 연결된 스냅이에요.';
        snapViewGrid.innerHTML = '';
        snapViewEmpty.style.display = 'none';
        snapViewModal.classList.add('show');
        window.api.get('/api/my-trips/' + tripId + '/snaps').then(function (result) {
            if (!result.success) { alert(result.message); closeSnapViewModal(); return; }
            var snaps = result.data || [];
            if (!snaps.length) { snapViewEmpty.style.display = ''; return; }
            snapViewGrid.innerHTML = snaps.map(snapCardHtml).join('');
        });
    }
    function closeSnapViewModal() { if (snapViewModal) snapViewModal.classList.remove('show'); }
    if (btnSnapViewClose) btnSnapViewClose.addEventListener('click', closeSnapViewModal);
    if (snapViewModal) snapViewModal.addEventListener('click', function (e) {
        if (e.target === snapViewModal) closeSnapViewModal();
    });

    btnAdd.addEventListener('click', function () { openModal(null); });
    btnCancel.addEventListener('click', closeModal);
    modal.addEventListener('click', function (e) { if (e.target === modal) closeModal(); });

    listEl.addEventListener('click', function (e) {
        var editBtn = e.target.closest('.trip-edit');
        if (editBtn) {
            var t = trips().filter(function (x) { return x.id === editBtn.dataset.id; })[0];
            if (t) openModal(t);
            return;
        }
        var delBtn = e.target.closest('.trip-del');
        if (delBtn) {
            if (!confirm('이 여행을 삭제할까요? 지도 색과 칭호 판정에도 반영됩니다.')) return;
            delBtn.disabled = true;
            window.api.del('/api/my-trips/' + delBtn.dataset.id).then(function (result) {
                if (!result.success) { alert(result.message); delBtn.disabled = false; return; }
                location.reload();
            });
        }
        var uploadBtn = e.target.closest('.trip-upload-snap');
        if (uploadBtn && window.openWriteModalWithTrip) {
            window.openWriteModalWithTrip(uploadBtn.dataset.id);
            return;
        }
        var viewBtn = e.target.closest('.trip-view-snaps');
        if (viewBtn) {
            openSnapViewModal(viewBtn.dataset.id, viewBtn.dataset.title);
        }
    });

    btnSave.addEventListener('click', function () {
        var title = fTitle.value.trim(), destination = fDest.value.trim();
        if (!title || !destination) { alert('여행 이름과 여행지를 입력해 주세요.'); return; }
        if (!fStart.value || !fEnd.value) { alert('가는 날과 오는 날을 입력해 주세요.'); return; }
        if (fEnd.value < fStart.value) { alert('오는 날짜는 가는 날짜보다 빠를 수 없어요.'); return; }

        var payload = {
            title: title, destination: destination,
            startDate: fStart.value, endDate: fEnd.value,
            memo: fMemo.value.trim() || null
        };
        btnSave.disabled = true;
        var id = fId.value;
        var req = id ? window.api.put('/api/my-trips/' + id, payload) : window.api.post('/api/my-trips', payload);
        req.then(function (result) {
            btnSave.disabled = false;
            if (!result.success) { alert(result.message); return; }
            location.reload();
        });
    });

    render();
})();

/**
 * 패키지 예약 흐름:
 * ① 예약 버튼 클릭 -> 날씨 미리보기 조회
 * ② 날씨 나쁘면 경고+대안 표시, "그래도 진행" / "취소" 선택
 * ③ 진행 선택 시 실제 예약 API 호출(weatherAck=true)
 *
 * URL 에 ?partyId=5 가 붙어 있으면(파티방 "패키지 보러가기"에서 넘어온 경우) 그 파티에
 * 예약을 연결한다 - 그래야 파티방에 계획표가 자동으로 생긴다.
 */
(function () {
    const btn = document.getElementById('btn-check-weather');
    const adviceBox = document.getElementById('weather-advice');
    let lastAdvice = null;

    const partyId = new URLSearchParams(window.location.search).get('partyId');
    if (partyId) {
        const banner = document.createElement('p');
        banner.style.cssText = 'background:#dcebd6; padding:10px; border-radius:8px; font-size:12px; margin-top:10px;';
        banner.textContent = '🎉 파티에 연결해서 예약합니다. 예약 완료 시 파티 전체 계획표가 자동으로 만들어져요.';
        btn.parentElement.insertBefore(banner, btn);
    }

    btn.addEventListener('click', async () => {
        const date = document.getElementById('departureDate').value;
        if (!date) { alert('출발일을 선택해 주세요.'); return; }

        btn.disabled = true;
        const result = await window.api.get(`/api/packages/${TOUR_ID}/weather?date=${date}`);
        btn.disabled = false;

        if (!result.success) { alert(result.message); return; }
        lastAdvice = result.data;
        renderAdvice(lastAdvice, date);
    });

    function renderAdvice(advice, date) {
        adviceBox.style.display = 'block';
        if (advice.recommend) {
            adviceBox.style.background = '#dcebd6';
            adviceBox.innerHTML = `<b>👍 ${advice.message}</b>
                <div style="margin-top:10px;"><button class="btn btn-primary" id="btn-do-reserve">예약 진행하기</button></div>`;
        } else {
            let altHtml = '';
            if (advice.alternatives && advice.alternatives.length) {
                altHtml = '<div style="margin-top:10px; font-size:12px;">다른 지역 추천: ' +
                    advice.alternatives.map(t => `<a href="/packages/${t.id}" style="margin-right:8px;">${t.title}</a>`).join('') +
                    '</div>';
            }
            adviceBox.style.background = '#f6e2d5';
            adviceBox.innerHTML = `<b>⚠️ ${advice.message}</b>${altHtml}
                <div style="margin-top:10px; display:flex; gap:8px;">
                    <button class="btn" id="btn-do-reserve" style="background:#fff;border:1px solid var(--line);">그래도 진행할게요</button>
                </div>`;
        }
        document.getElementById('btn-do-reserve').addEventListener('click', () => doReserve(date));
    }

    async function doReserve(date) {
        const peopleCount = parseInt(document.getElementById('peopleCount').value, 10) || 1;
        const weatherAck = !!(lastAdvice && !lastAdvice.recommend);

        const result = await window.api.post(`/api/packages/${TOUR_ID}/reserve`, {
            tourId: TOUR_ID, partyId: partyId ? parseInt(partyId, 10) : null, departureDate: date, peopleCount, weatherAck
        });
        if (!result.success) { alert(result.message); return; }
        window.location.href = `/reservations/${result.data}`;
    }
})();

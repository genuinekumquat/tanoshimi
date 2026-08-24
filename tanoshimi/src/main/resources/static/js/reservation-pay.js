/** 내 예약 목록의 "결제하기" 버튼 - 패키지 대금을 포인트로 결제한다. */
(function () {
    document.querySelectorAll('.btn-pay-reservation').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.preventDefault();
            if (!confirm('이 금액으로 결제할까요? (보유 포인트에서 차감됩니다)')) return;
            btn.disabled = true;
            const result = await window.api.post(`/api/reservations/${btn.dataset.reservationId}/pay`, {});
            btn.disabled = false;
            if (result.success) {
                alert(result.message);
                location.reload();
            } else {
                alert(result.message || '결제에 실패했습니다.');
            }
        });
    });
})();

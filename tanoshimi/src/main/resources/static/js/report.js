/**
 * 게시글/파티/사용자 공용 신고 버튼 로직.
 * data-report-type="post|party|user", data-report-id, data-report-label 속성이 붙은
 * 버튼이라면 어디서든 이 스크립트 하나로 신고가 동작한다(이벤트 위임이라 페이지마다
 * 따로 등록할 필요 없음).
 */
document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-report-type]');
    if (!btn) return;

    const targetType = btn.dataset.reportType;
    const targetId = btn.dataset.reportId;
    const targetLabel = btn.dataset.reportLabel || '';

    const reason = prompt(`'${targetLabel}' 신고 사유를 알려주세요.`);
    if (!reason || !reason.trim()) return;

    const result = await window.api.post('/api/reports', {
        targetType, targetId: parseInt(targetId, 10), targetLabel, reason: reason.trim()
    });
    alert(result.message || (result.success ? '신고가 접수되었습니다.' : '신고 접수에 실패했습니다.'));
});

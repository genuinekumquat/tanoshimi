/**
 * 계획표 방장 전용 메뉴 - 편집권(lock) 관리 + 10분 주기 자동저장 + 스냅샷 롤백.
 * 이 파일은 IS_PARTY_OWNER 가 false 인 일반 파티원 화면에서도 로드는 되지만
 * (owner-menu 자체가 th:if 로 화면에 안 그려지므로) 실질적으로 아무 동작도 하지 않는다.
 */
(function () {
    const AUTO_SAVE_INTERVAL_MS = 10 * 60 * 1000; // 10분

    // ---------------- 편집권 없으면 편집 UI 자체를 막는다 ----------------
    if (!IS_LOCK_HOLDER) {
        const addBtn = document.getElementById('btn-add');
        const clearBtn = document.getElementById('btn-clear');
        if (addBtn) { addBtn.disabled = true; addBtn.title = '지금은 편집권이 있는 사람만 일정을 추가할 수 있어요.'; addBtn.style.opacity = '0.5'; addBtn.style.cursor = 'not-allowed'; }
        if (clearBtn) { clearBtn.disabled = true; clearBtn.style.opacity = '0.5'; clearBtn.style.cursor = 'not-allowed'; }
    }

    // ---------------- 방장 메뉴 펼치기/접기 ----------------
    const toggle = document.getElementById('owner-menu-toggle');
    const body = document.getElementById('owner-menu-body');
    const arrow = document.getElementById('owner-menu-arrow');
    if (toggle && body) {
        toggle.addEventListener('click', () => {
            const opened = body.style.display !== 'none';
            body.style.display = opened ? 'none' : 'block';
            arrow.textContent = opened ? '펼치기 ▾' : '접기 ▴';
            if (!opened) loadSnapshots();
        });
    }

    // ---------------- 편집권 부여/회수 ----------------
    document.getElementById('btn-grant-lock')?.addEventListener('click', async () => {
        const select = document.getElementById('lock-target-select');
        const targetUserId = select.value;
        if (!targetUserId) { alert('편집권을 줄 파티원을 선택해 주세요.'); return; }
        const result = await window.api.post(`/api/planner/${SCHEDULE_ID}/lock/${targetUserId}`, {});
        alert(result.message);
        if (result.success) location.reload();
    });

    document.getElementById('btn-revoke-lock')?.addEventListener('click', async () => {
        if (!confirm('편집권을 회수해서 방장인 나에게 다시 가져올까요?')) return;
        const result = await window.api.del(`/api/planner/${SCHEDULE_ID}/lock`);
        alert(result.message);
        if (result.success) location.reload();
    });

    // ---------------- 저장(수동 + 10분 자동) ----------------
    async function save(trigger) {
        const result = await window.api.post(`/api/planner/${SCHEDULE_ID}/save?trigger=${trigger}`, {});
        if (trigger === 'manual') {
            alert(result.message || (result.success ? '저장되었습니다.' : '저장에 실패했습니다.'));
            if (result.success) loadSnapshots();
        }
        return result.success;
    }

    document.getElementById('btn-manual-save')?.addEventListener('click', () => save('manual'));

    if (IS_PARTY_OWNER) {
        // 방장이 화면을 열어둔 동안 10분마다 자동으로 저장 + 스냅샷 적재.
        setInterval(() => save('auto'), AUTO_SAVE_INTERVAL_MS);
    }

    // ---------------- 저장 시점 타임라인 + 롤백 ----------------
    async function loadSnapshots() {
        const list = document.getElementById('snapshot-list');
        if (!list) return;
        const result = await window.api.get(`/api/planner/${SCHEDULE_ID}/snapshots`);
        if (!result.success || !result.data || result.data.length === 0) {
            list.innerHTML = '<div style="padding:12px; font-size:12px; color:var(--ink-soft);">아직 저장된 시점이 없어요.</div>';
            return;
        }
        list.innerHTML = result.data.map(s => {
            const label = s.triggerType === 'auto' ? '자동저장' : '수동저장';
            const when = new Date(s.createdAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
            return `
                <div style="display:flex; justify-content:space-between; align-items:center; padding:9px 12px; border-bottom:1px solid var(--line); font-size:12.5px;">
                    <span>${when} · ${label} · ${s.createdByName}</span>
                    <button type="button" class="btn btn-ghost btn-sm btn-rollback" data-snapshot-id="${s.id}" style="font-size:11px; padding:3px 9px;">이 시점으로</button>
                </div>`;
        }).join('');

        list.querySelectorAll('.btn-rollback').forEach(btn => {
            btn.addEventListener('click', async () => {
                if (!confirm('이 시점으로 되돌릴까요? 지금 상태는 자동으로 먼저 저장해 둡니다.')) return;
                const result = await window.api.post(`/api/planner/${SCHEDULE_ID}/snapshots/${btn.dataset.snapshotId}/rollback`, {});
                alert(result.message);
                if (result.success) location.reload();
            });
        });
    }

    if (IS_PARTY_OWNER) {
        loadSnapshots();
    }
})();

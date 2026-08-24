/**
 * 우측 상단 알림 벨.
 * 로그인 상태에서만 마크업이 렌더링되므로(sec:authorize), 엘리먼트가 없으면 그냥 조용히 종료한다.
 */
(function () {
    const bell = document.getElementById('notif-bell');
    const badge = document.getElementById('notif-badge');
    const dropdown = document.getElementById('notif-dropdown');
    const list = document.getElementById('notif-list');
    if (!bell) return;

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    async function refreshBadge() {
        const result = await window.api.get('/api/notifications/unread-count');
        if (!result.success) return;
        const count = result.data;
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    }

    async function loadList() {
        list.innerHTML = '<p class="notif-empty">불러오는 중...</p>';
        const result = await window.api.get('/api/notifications');
        if (!result.success) { list.innerHTML = '<p class="notif-empty">불러오지 못했습니다.</p>'; return; }
        const items = result.data;
        if (!items.length) { list.innerHTML = '<p class="notif-empty">알림이 없습니다.</p>'; return; }

        list.innerHTML = items.map(n => `
            <a class="notif-item ${n.read ? 'read' : 'unread'}" href="${n.linkUrl ? escapeHtml(n.linkUrl) : '#'}" data-id="${n.id}">
                <div class="notif-item-title">${escapeHtml(n.title)}</div>
                <div class="notif-item-message">${escapeHtml(n.message)}</div>
            </a>`).join('');

        list.querySelectorAll('.notif-item').forEach(el => {
            el.addEventListener('click', async () => {
                await window.api.post(`/api/notifications/${el.dataset.id}/read`, {});
            });
        });
    }

    let open = false;
    bell.addEventListener('click', async (e) => {
        e.stopPropagation();
        open = !open;
        dropdown.style.display = open ? 'block' : 'none';
        if (open) {
            await loadList();
            await refreshBadge();
        }
    });
    document.addEventListener('click', (e) => {
        if (open && !dropdown.contains(e.target) && e.target !== bell) {
            open = false;
            dropdown.style.display = 'none';
        }
    });

    refreshBadge();
    setInterval(refreshBadge, 60000); // 1분마다 배지 갱신
})();

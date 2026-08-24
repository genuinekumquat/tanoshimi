import re
import os

# 1. Update layout.html to include current user ID, JS libs, and notifications.js
def patch_layout():
    path = 'src/main/resources/templates/fragments/layout.html'
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()

    # Meta tag for user id
    if 'name="current-user-id"' not in text:
        text = text.replace('<head>', '''<head>
    <meta name="current-user-id" sec:authorize="isAuthenticated()" th:content="${#authentication.principal.id}">''')

    # Add stomp and sockjs and notifications.js at the end of head
    if 'sockjs-client' not in text:
        text = text.replace('</head>', '''    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
    <script th:src="@{/js/notifications.js}" defer></script>
</head>''')

    # Change ID in HTML? The HTML uses top-notif-bell but we'll change JS to match HTML.
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

# 2. Update notifications.js
def patch_notifications_js():
    path = 'src/main/resources/static/js/notifications.js'
    with open(path, 'w', encoding='utf-8') as f:
        f.write('''/**
 * 통합 알림 및 실시간 Toast (WebSocket)
 */
(function () {
    const bell = document.getElementById('top-notif-bell');
    const badge = document.getElementById('top-notif-badge');
    const dropdown = document.getElementById('top-notif-dropdown');
    const list = document.getElementById('top-notif-list');
    
    // Toast Container 추가
    const toastContainer = document.createElement('div');
    toastContainer.style.cssText = 'position:fixed; bottom:20px; right:20px; z-index:9999; display:flex; flex-direction:column; gap:10px;';
    document.body.appendChild(toastContainer);

    function showToast(title, message, linkUrl) {
        const toast = document.createElement('div');
        toast.style.cssText = 'background:#fff; border-left:4px solid var(--forest,#2e7d32); box-shadow:0 10px 30px rgba(0,0,0,0.1); border-radius:8px; padding:16px 20px; width:300px; transform:translateX(120%); transition:transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); cursor:pointer;';
        toast.innerHTML = `<div style="font-size:14px; font-weight:bold; margin-bottom:4px; color:#333;">${title}</div><div style="font-size:13px; color:#666; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;">${message}</div>`;
        
        toast.onclick = () => { if(linkUrl) location.href = linkUrl; };
        toastContainer.appendChild(toast);
        
        requestAnimationFrame(() => toast.style.transform = 'translateX(0)');
        
        setTimeout(() => {
            toast.style.transform = 'translateX(120%)';
            setTimeout(() => toast.remove(), 300);
        }, 5000);
    }

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
            badge.style.display = 'flex';
        } else {
            badge.style.display = 'none';
        }
    }

    async function loadList() {
        list.innerHTML = '<p class="notif-empty" style="text-align:center; padding:20px 0; color:#999; font-size:13px;">불러오는 중...</p>';
        const result = await window.api.get('/api/notifications');
        if (!result.success) { list.innerHTML = '<p class="notif-empty" style="text-align:center; padding:20px 0; color:#999; font-size:13px;">불러오지 못했습니다.</p>'; return; }
        const items = result.data;
        if (!items.length) { list.innerHTML = '<p class="notif-empty" style="text-align:center; padding:20px 0; color:#999; font-size:13px;">새로운 알림이 없습니다.</p>'; return; }

        list.innerHTML = items.map(n => `
            <a class="notif-item ${n.read ? 'read' : 'unread'}" href="${n.linkUrl ? escapeHtml(n.linkUrl) : '#'}" data-id="${n.id}" style="display:block; padding:12px 16px; text-decoration:none; border-bottom:1px solid #f0f0f0; ${n.read?'background:#fff;':'background:#f4f9f5;'}">
                <div class="notif-item-title" style="font-size:13.5px; font-weight:bold; color:#222; margin-bottom:4px;">${escapeHtml(n.title)}</div>
                <div class="notif-item-message" style="font-size:12.5px; color:#666;">${escapeHtml(n.message)}</div>
            </a>`).join('');

        list.querySelectorAll('.notif-item').forEach(el => {
            el.addEventListener('click', async (e) => {
                if(e.currentTarget.classList.contains('unread')) {
                    await window.api.post(`/api/notifications/${el.dataset.id}/read`, {});
                }
            });
        });
    }

    let open = false;
    bell.addEventListener('click', async (e) => {
        e.stopPropagation();
        open = !open;
        if (open) {
            dropdown.style.display = 'block';
            await loadList();
            refreshBadge();
        } else {
            dropdown.style.display = 'none';
        }
    });

    document.addEventListener('click', (e) => {
        if (open && !dropdown.contains(e.target)) {
            dropdown.style.display = 'none';
            open = false;
        }
    });

    refreshBadge();
    setInterval(refreshBadge, 60000);

    // WebSocket 연결 (STOMP)
    const metaTag = document.querySelector('meta[name="current-user-id"]');
    if (metaTag && typeof SockJS !== 'undefined' && typeof Stomp !== 'undefined') {
        const userId = metaTag.content;
        const socket = new SockJS('/ws');
        const stompClient = Stomp.over(socket);
        stompClient.debug = null; // disable console logging
        
        stompClient.connect({}, function (frame) {
            stompClient.subscribe('/topic/user.' + userId + '.notifications', function (message) {
                const notif = JSON.parse(message.body);
                showToast(notif.title, notif.message, notif.linkUrl);
                refreshBadge();
                if (open) loadList();
            });
        });
    }
})();
''')

patch_layout()
patch_notifications_js()

print("Patched Notification stuff!")
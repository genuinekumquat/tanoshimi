(function () {
    const likeBtn = document.getElementById('btn-like');
    if (likeBtn) {
        likeBtn.addEventListener('click', async () => {
            likeBtn.disabled = true;
            const result = await window.api.post(`/api/posts/${likeBtn.dataset.postId}/like`, {});
            likeBtn.disabled = false;
            if (result.success) {
                location.reload();
            } else {
                // 실패해도 아무 반응이 없으면 사용자 입장에선 "눌러도 무응답"으로 보이니 반드시 알려준다.
                alert(result.message || '좋아요 처리에 실패했습니다.');
            }
        });
    }

    const commentBtn = document.getElementById('btn-comment-submit');
    if (commentBtn) {
        commentBtn.addEventListener('click', async () => {
            const input = document.getElementById('comment-input');
            const content = input.value.trim();
            if (!content) return;
            try {
                const response = await fetch(`/api/posts/${commentBtn.dataset.postId}/comments`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json',
                        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content },
                    body: JSON.stringify({ content: content })
                });
                const result = await response.json();
                if (result.success) location.reload(); else alert(result.message || '댓글 등록에 실패했습니다.');
            } catch (e) {
                console.error('댓글 등록 오류:', e);
                alert('댓글 등록 중 오류가 발생했습니다.');
            }
        });
    }

    const deleteBtn = document.getElementById('btn-delete-post');
    if (deleteBtn) {
        deleteBtn.addEventListener('click', async () => {
            if (!confirm('이 글을 삭제할까요? 되돌릴 수 없습니다.')) return;
            const result = await window.api.del(`/api/posts/${deleteBtn.dataset.postId}`);
            if (result.success) window.location.href = '/board';
            else alert(result.message || '삭제에 실패했습니다.');
        });
    }

    // 공유하기 - 이 글의 URL을 클립보드에 복사한다.
    const shareBtn = document.getElementById('btn-share');
    if (shareBtn) {
        shareBtn.addEventListener('click', async () => {
            const url = window.location.href;
            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(url);
                } else {
                    // http(비보안 컨텍스트, 예: http://localhost) 에서는 clipboard API 가 막혀 있을 수 있어서
                    // 옛날 방식(임시 textarea + execCommand)으로 폴백한다.
                    const textarea = document.createElement('textarea');
                    textarea.value = url;
                    textarea.style.position = 'fixed';
                    textarea.style.opacity = '0';
                    document.body.appendChild(textarea);
                    textarea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textarea);
                }
                const original = shareBtn.textContent;
                shareBtn.textContent = '✅ 링크 복사됨';
                setTimeout(() => { shareBtn.textContent = original; }, 1500);
            } catch (e) {
                console.error('클립보드 복사 실패:', e);
                alert('링크 복사에 실패했습니다. 주소창의 URL을 직접 복사해 주세요.');
            }
        });
    }
})();

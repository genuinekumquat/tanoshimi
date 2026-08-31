/**
 * 마이페이지 프로필 편집(자기소개 저장). 담당: 김민규(⑥).
 *
 * 이전에는 저장 버튼이 화면의 글자만 바꾸고 끝나서 새로고침하면 되돌아갔다.
 * 이제 POST /api/mypage/intro 로 실제 저장한다. CSRF 는 csrf.js 의 window.api 가 붙인다.
 */
(function () {
    'use strict';

    var INTRO_MAX_LENGTH = 300;
    var EMPTY_TEXT = '아직 자기소개가 없습니다.';

    var text = document.getElementById('intro-text');
    var textarea = document.getElementById('intro-textarea');
    var actions = document.getElementById('intro-actions');
    var btnEdit = document.getElementById('btn-intro');
    var btnSave = document.getElementById('btn-intro-save');
    var btnCancel = document.getElementById('btn-intro-cancel');
    if (!text || !textarea || !actions || !btnEdit || !btnSave || !btnCancel) return;

    var toastTimer;
    function toast(message) {
        var el = document.getElementById('toast');
        if (!el) return;
        el.textContent = message;
        el.classList.add('on');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.classList.remove('on'); }, 1800);
    }

    function openEditor() {
        var current = text.textContent.trim();
        textarea.value = current === EMPTY_TEXT ? '' : current;
        text.style.display = 'none';
        textarea.style.display = 'block';
        actions.style.display = 'flex';
        textarea.focus();
    }

    function closeEditor() {
        text.style.display = 'block';
        textarea.style.display = 'none';
        actions.style.display = 'none';
    }

    btnEdit.addEventListener('click', openEditor);
    btnCancel.addEventListener('click', closeEditor);

    btnSave.addEventListener('click', async function () {
        if (!window.api) {
            // csrf.js 가 안 실려 있으면 CSRF 토큰 없이 요청이 나가 403 이 된다.
            toast('저장 기능을 불러오지 못했어요. 새로고침해 주세요');
            return;
        }
        var intro = textarea.value.trim();
        if (intro.length > INTRO_MAX_LENGTH) {
            toast('자기소개는 ' + INTRO_MAX_LENGTH + '자까지 쓸 수 있어요');
            return;
        }

        btnSave.disabled = true;
        try {
            var result = await window.api.post('/api/mypage/intro', { intro: intro });
            if (!result.success) {
                toast(result.message || '저장에 실패했어요');
                return;
            }
            text.textContent = intro || EMPTY_TEXT;
            closeEditor();
            toast('자기소개를 저장했어요');
        } finally {
            btnSave.disabled = false;
        }
    });
})();

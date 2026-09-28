/**
 * [FR-MYP-08] 마이페이지 프로필 배경 꾸미기.
 *
 * "🎨 배경 꾸미기" 버튼으로 스와치 선택기를 열고 닫는다. 스와치를 고르면 카드에 바로
 * 입혀 보이고 POST /api/mypage/theme 로 저장한다 - 실패하면 이전 테마로 되돌린다.
 * 테마 목록·색은 서버(UserProfileThemeService.THEMES)와 app.css 가 정한다.
 */
(function () {
    'use strict';

    var card = document.getElementById('profile-card');
    var btn = document.getElementById('btn-theme');
    var picker = document.getElementById('theme-picker');
    if (!card || !btn || !picker) return;

    var current = card.dataset.theme;   // 화면에 입혀진 테마
    var saved = current;                // 서버에 저장이 확인된 테마
    var seq = 0;

    var toastTimer;
    function toast(message) {
        var el = document.getElementById('toast');
        if (!el) return;
        el.textContent = message;
        el.classList.add('on');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.classList.remove('on'); }, 1800);
    }

    function applyTheme(key) {
        card.classList.remove('profile-theme-' + card.dataset.theme);
        card.classList.add('profile-theme-' + key);
        card.dataset.theme = key;
    }

    function checkRadio(key) {
        var radio = picker.querySelector('input[value="' + key + '"]');
        if (radio) radio.checked = true;
    }

    btn.addEventListener('click', function () {
        var open = picker.classList.toggle('on');
        btn.setAttribute('aria-expanded', String(open));
        if (open) {
            var checked = picker.querySelector('input:checked');
            if (checked) checked.focus();
        }
    });

    picker.addEventListener('change', async function (e) {
        var key = e.target.value;
        if (!key || key === current) return;

        if (!window.api) {
            // csrf.js 가 안 실려 있으면 CSRF 토큰 없이 요청이 나가 403 이 된다.
            checkRadio(current);
            toast('저장 기능을 불러오지 못했어요. 새로고침해 주세요');
            return;
        }

        applyTheme(key);
        current = key;

        // 방향키로 스와치를 훑으면 change 가 연달아 온다. 응답 순서가 뒤섞여도
        // 마지막으로 고른 것만 반영하고, 실패하면 서버에 마지막으로 저장된 테마로 돌린다.
        var mySeq = ++seq;
        var result = await window.api.post('/api/mypage/theme', { themeKey: key });
        if (result.success) saved = key;
        if (mySeq !== seq) return;

        if (!result.success) {
            applyTheme(saved);
            current = saved;
            checkRadio(saved);
            toast(result.message || '배경을 저장하지 못했어요');
            return;
        }
        toast('배경을 바꿨어요');
    });
})();

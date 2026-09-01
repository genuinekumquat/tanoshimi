/**
 * 칭호 관리 페이지(/mypage/titles) 전용. 담당: 김민규(⑥). [v20 신규]
 *
 * [v20] 예전에는 mypage-titles.js 안에 있었고, "더보기" 버튼을 누르면 같은 /mypage 페이지
 * 안에서 #view-mypage 를 감추고 #view-titles 를 보여주는 방식(JS 뷰 전환)이었다. 그러다 보니
 * 주소가 항상 /mypage 그대로라 뒤로가기를 누르면 칭호 관리 화면이 아니라 브라우저 히스토리상
 * 그 전 페이지(보통 메인 페이지)로 나가버리는 문제가 있었다. 그래서 진짜 URL이 있는
 * /mypage/mytrip 페이지로 뜯어냈다 - 뷰 전환 코드(vMy/vT/버튼 토글)는 필요 없어졌고, 페이지가
 * 열리자마자 바로 그린다. 나머지 로직(칭호 카탈로그 렌더링, 대표 칭호 수정 모달, 툴팁)은
 * 원래 mypage-titles.js 에 있던 것을 그대로 옮겼다.
 *
 * 칭호 38종 8카테고리는 DB(titles)가 정본이다. 템플릿이 전체 목록을 #titles-data 에
 * data-* 로 내려주고(카테고리 순서도 서버가 정함), 이 파일은 그걸 그대로 그린다.
 * 판정(누가 무엇을 받았는지)은 TitleService 가 한다.
 *
 * ※ 대표 칭호 변경은 아직 저장 API가 없어서 화면 상태만 바뀐다(Phase 2에서
 *   user_titles에 대표 여부 컬럼 + PATCH API 필요).
 */
(function () {
    'use strict';

    var meta = document.getElementById('titles-meta');
    var REP_TITLE = (meta && meta.dataset.repTitle) || '';

    var TITLES = [].map.call(
        document.querySelectorAll('#titles-data span'),
        function (el) {
            return {
                code: el.dataset.code || '',
                name: el.dataset.name || '',
                cat: el.dataset.category || '기타',
                cond: el.dataset.cond || '',
                icon: el.dataset.icon || '🏷️',
                owned: el.dataset.owned === 'true'
            };
        }
    ).filter(function (t) { return t.name; });

    function findByName(name) {
        for (var i = 0; i < TITLES.length; i++) { if (TITLES[i].name === name) return TITLES[i]; }
        return null;
    }

    // 대표 칭호: 서버가 정한 값에서 출발. 변경은 아직 화면 상태만(저장 API는 Phase 2).
    var rep = findByName(REP_TITLE) || TITLES.filter(function (t) { return t.owned; })[0] || null;

    var tgridEl = document.getElementById('tgrid');
    if (!tgridEl) return; // 이 스크립트가 실수로 다른 페이지에 실렸을 때를 대비한 안전장치.

    function esc(s) {
        return String(s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    function renderRepCard() {
        var badge = document.getElementById('rep-badge'),
            name = document.getElementById('rep-name'),
            cond = document.getElementById('rep-cond');
        if (!badge) return;
        if (!rep) {
            badge.textContent = '🔒'; name.textContent = '아직 없음';
            cond.textContent = '첫 여행을 완료하면 칭호가 열려요';
            return;
        }
        badge.textContent = rep.icon; name.textContent = rep.name; cond.textContent = rep.cond;
    }

    function cardHTML(t) {
        if (t.owned) {
            var isRep = rep && t.name === rep.name;
            return '<div class="tcard owned' + (isRep ? ' rep' : '') + '" data-cond="' + esc(t.cond) + '">' +
                (isRep ? '<span class="tc-badge on">대표</span>' : '') +
                '<div class="tc-ic">' + t.icon + '</div><div class="tc-name">' + esc(t.name) + '</div></div>';
        }
        return '<div class="tcard locked" data-cond="' + esc(t.cond) + '">' +
            '<span class="tc-lock">🔒 미획득</span>' +
            '<div class="tc-ic">' + t.icon + '</div><div class="tc-name">' + esc(t.name) + '</div></div>';
    }

    function renderTGrid() {
        // 서버가 카테고리 순서대로 내려주므로, 순서를 새로 정하지 않고 나온 순서대로 묶는다.
        var order = [], groups = {};
        TITLES.forEach(function (t) {
            if (!groups[t.cat]) { groups[t.cat] = []; order.push(t.cat); }
            groups[t.cat].push(t);
        });
        var html = '';
        order.forEach(function (cat) {
            var items = groups[cat];
            var have = items.filter(function (t) { return t.owned; }).length;
            html += '<div class="tcat"><span>' + esc(cat) + '</span>' +
                '<span class="tcat-count">' + have + '/' + items.length + '</span></div>';
            html += items.map(cardHTML).join('');
        });
        tgridEl.innerHTML = html;
    }

    renderRepCard();
    renderTGrid();

    // 칭호 호버 시 조건 툴팁 - 잠긴 칭호는 해금 조건, 이미 딴 칭호는 달성 조건을 보여준다.
    var tip = document.getElementById('tc-tip');
    if (tip) {
        tgridEl.addEventListener('mouseover', function (e) {
            var c = e.target.closest('.tcard');
            if (!c || !c.dataset.cond) return;
            tip.textContent = (c.classList.contains('locked') ? '🔒 ' : '✅ ') + c.dataset.cond;
            tip.classList.add('show');
        });
        tgridEl.addEventListener('mousemove', function (e) {
            var c = e.target.closest('.tcard');
            if (!c || !c.dataset.cond) { tip.classList.remove('show'); return; }
            tip.style.left = (e.clientX - tip.offsetWidth / 2) + 'px';
            tip.style.top = (e.clientY - tip.offsetHeight - 12) + 'px';
        });
        tgridEl.addEventListener('mouseout', function (e) {
            if (e.target.closest('.tcard')) tip.classList.remove('show');
        });
    }

    // 대표 칭호 수정 모달
    var modal = document.getElementById('rep-modal'), mList = document.getElementById('m-list');
    var pending = rep;
    if (modal && mList) {
        document.getElementById('btn-rep-edit').addEventListener('click', function () {
            pending = rep;
            var owned = TITLES.filter(function (t) { return t.owned; });
            mList.innerHTML = owned.length
                ? owned.map(function (t) {
                    return '<div class="m-opt' + (rep && t.name === rep.name ? ' sel' : '') + '" data-name="' + esc(t.name) + '">' +
                        '<span class="mo-ic">' + t.icon + '</span><span class="mo-name">' + esc(t.name) + '</span></div>';
                }).join('')
                : '<p style="grid-column:1/-1; font-size:13px; color:var(--ink-soft); margin:0;">아직 획득한 칭호가 없어요.</p>';
            modal.classList.add('show');
        });
        mList.addEventListener('click', function (e) {
            var o = e.target.closest('.m-opt');
            if (!o) return;
            pending = findByName(o.dataset.name);
            [].forEach.call(mList.children, function (c) { c.classList.toggle('sel', c === o); });
        });
        document.getElementById('m-cancel').addEventListener('click', function () { modal.classList.remove('show'); });
        modal.addEventListener('click', function (e) { if (e.target === modal) modal.classList.remove('show'); });
        document.getElementById('m-confirm').addEventListener('click', function () {
            rep = pending;
            modal.classList.remove('show');
            renderRepCard(); renderTGrid();
            // TODO(Phase 2): 대표 칭호 저장 API 연결. 지금은 새로고침하면 서버 값으로 돌아간다.
            toast('대표 칭호를 바꿨어요 (저장 기능은 준비 중이에요)');
        });
    }

    var toastTimer;
    function toast(message) {
        var el = document.getElementById('toast');
        if (!el) return;
        el.textContent = message;
        el.classList.add('on');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.classList.remove('on'); }, 2200);
    }
})();

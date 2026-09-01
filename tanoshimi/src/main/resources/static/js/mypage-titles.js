/**
 * 마이페이지 칭호 관리 뷰 + 여행 거리. 담당: 김민규(⑥).
 *
 * 지도 목업(아티팩트)의 칭호/거리 로직을 실제 코드로 이식한 버전.
 *
 * ── 칭호 ─────────────────────────────────────────────────────────────
 * "내 칭호" 칩(획득분만)은 서버가 Thymeleaf로 이미 그려준다. 이 파일은 "더보기"를 눌렀을 때
 * 나오는 칭호 관리 뷰(카테고리별 전체 목록 + 미획득 잠금 카드 + 대표칭호 수정)를 담당한다.
 *
 * 칭호 38종 8카테고리는 DB(titles)가 정본이다. 템플릿이 전체 목록을 #titles-data 에
 * data-* 로 내려주고(카테고리 순서도 서버가 정함), 이 파일은 그걸 그대로 그린다.
 * 판정(누가 무엇을 받았는지)은 TitleService 가 한다.
 *
 * ※ 대표 칭호 변경은 아직 저장 API가 없어서 화면 상태만 바뀐다(Phase 2에서
 *   user_titles에 대표 여부 컬럼 + PATCH API 필요).
 *
 * ── 여행 거리 ─────────────────────────────────────────────────────────
 * 집(출발지) 기준 하버사인 왕복 누적. 방문 횟수는 서버 히트맵(window.MYPAGE_HEATMAP)의
 * 실제 완료 여행 집계를 그대로 쓰고, 좌표는 아래 COORDS 표(시도청/현청 소재지 기준)를 쓴다.
 * 집 주소만 아직 DB에 없어서(users.home_lat/lng = Phase 2, ⑤ 허수연) 브라우저
 * localStorage에 임시 저장한다. 컬럼이 생기면 loadHome/saveHome만 API로 바꾸면 된다.
 */
(function () {
    'use strict';

    /* ===================== 서버가 넘겨준 값 읽기 =====================
     * Thymeleaf 인라인 자바스크립트로 객체를 통째로 직렬화하면 엔티티 필드가 그대로
     * 노출되고 프록시 때문에 깨지기도 해서, 화면에 이미 그려진 마크업의 data-* 속성에서
     * 읽는다(단일 출처). 지역 집계만 예외로 window.MYPAGE_HEATMAP 을 그대로 쓴다.
     */
    var meta = document.getElementById('mypage-meta');
    var USER_KEY = (meta && meta.dataset.userKey) || 'guest';
    var REP_TITLE = (meta && meta.dataset.repTitle) || '';

    /* =========================== 여행 거리 =========================== */

    // 시도청/현청 소재지 좌표. 지도(regions.json)의 지역명과 같은 표기를 키로 쓴다.
    var COORDS = {
        // 한국 17개 시도
        '서울': [37.5665, 126.9780], '부산': [35.1796, 129.0756], '대구': [35.8714, 128.6014],
        '인천': [37.4563, 126.7052], '광주': [35.1595, 126.8526], '대전': [36.3504, 127.3845],
        '울산': [35.5384, 129.3114], '세종': [36.4800, 127.2890], '경기': [37.4138, 127.5183],
        '강원': [37.8228, 128.1555], '충북': [36.6357, 127.4917], '충남': [36.6588, 126.6728],
        '전북': [35.8203, 127.1088], '전남': [34.8161, 126.4630], '경북': [36.5760, 128.5056],
        '경남': [35.2383, 128.6924], '제주': [33.4996, 126.5312],
        // 일본 47개 도도부현
        '홋카이도': [43.0642, 141.3469], '아오모리': [40.8244, 140.7400], '이와테': [39.7036, 141.1527],
        '미야기': [38.2688, 140.8721], '아키타': [39.7186, 140.1024], '야마가타': [38.2404, 140.3633],
        '후쿠시마': [37.7503, 140.4676], '이바라키': [36.3418, 140.4468], '도치기': [36.5657, 139.8836],
        '군마': [36.3907, 139.0604], '사이타마': [35.8570, 139.6489], '지바': [35.6051, 140.1233],
        '도쿄': [35.6895, 139.6917], '가나가와': [35.4478, 139.6425], '니가타': [37.9026, 139.0236],
        '도야마': [36.6953, 137.2114], '이시카와': [36.5947, 136.6256], '후쿠이': [36.0652, 136.2216],
        '야마나시': [35.6642, 138.5684], '나가노': [36.6513, 138.1810], '기후': [35.3912, 136.7223],
        '시즈오카': [34.9769, 138.3831], '아이치': [35.1802, 136.9066], '미에': [34.7303, 136.5086],
        '시가': [35.0045, 135.8686], '교토': [35.0116, 135.7681], '오사카': [34.6937, 135.5023],
        '효고': [34.6913, 135.1830], '나라': [34.6851, 135.8048], '와카야마': [34.2260, 135.1675],
        '돗토리': [35.5039, 134.2377], '시마네': [35.4723, 133.0505], '오카야마': [34.6618, 133.9350],
        '히로시마': [34.3853, 132.4553], '야마구치': [34.1859, 131.4714], '도쿠시마': [34.0658, 134.5593],
        '카가와': [34.3401, 134.0434], '에히메': [33.8416, 132.7657], '고치': [33.5597, 133.5311],
        '후쿠오카': [33.5904, 130.4017], '사가': [33.2494, 130.2988], '나가사키': [32.7448, 129.8737],
        '구마모토': [32.7898, 130.7417], '오이타': [33.2382, 131.6126], '미야자키': [31.9111, 131.4239],
        '가고시마': [31.5602, 130.5581], '오키나와': [26.2124, 127.6809]
    };

    // 집으로 고를 수 있는 곳(한국 17개 시도). Phase 2에서 주소 입력 + 지오코딩으로 대체.
    var HOME_OPTIONS = ['서울', '부산', '대구', '인천', '광주', '대전', '울산', '세종',
        '경기', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주'];

    // mypage-heatmap.js 와 같은 별칭 표. 서버 지역명 표기가 흔들려도 좌표를 찾게 해준다.
    var NAME_ALIASES = {
        '서울특별시': '서울', '경기도': '경기', '인천광역시': '인천',
        '강원특별자치도': '강원', '강원도': '강원',
        '충청북도': '충북', '충청남도': '충남', '세종특별자치시': '세종',
        '전라북도': '전북', '전북특별자치도': '전북', '전라남도': '전남',
        '경상북도': '경북', '경상남도': '경남',
        '대구광역시': '대구', '부산광역시': '부산', '광주광역시': '광주',
        '대전광역시': '대전', '울산광역시': '울산', '제주특별자치도': '제주', '제주도': '제주'
    };

    var HOME_KEY = 'tanoshimi.mypage.home.' + USER_KEY;

    function loadHome() {
        try {
            var saved = localStorage.getItem(HOME_KEY);
            if (saved && COORDS[saved]) return saved;
        } catch (e) { /* 사생활 보호 모드 등에서 접근이 막히면 그냥 기본값 */ }
        return '서울';
    }
    function saveHome(name) {
        try { localStorage.setItem(HOME_KEY, name); } catch (e) { /* 저장 못 해도 화면은 동작 */ }
    }

    function haversine(a, b) {
        var R = 6371,
            dLat = (b[0] - a[0]) * Math.PI / 180,
            dLon = (b[1] - a[1]) * Math.PI / 180,
            la1 = a[0] * Math.PI / 180, la2 = b[0] * Math.PI / 180;
        var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    /** 서버 히트맵의 완료 여행 횟수 x 왕복 거리 누적. */
    function totalDistance(home) {
        var raw = (window.MYPAGE_HEATMAP && window.MYPAGE_HEATMAP.regions) || {};
        var hc = COORDS[home];
        if (!hc) return 0;
        var sum = 0;
        Object.keys(raw).forEach(function (name) {
            var norm = NAME_ALIASES[name] || name;
            var c = COORDS[norm];
            if (!c || norm === home) return;
            sum += haversine(hc, c) * (raw[name].trips || 0) * 2; // 왕복
        });
        return Math.round(sum);
    }

    var home = loadHome();
    var distKm = document.getElementById('dist-km');
    var homeName = document.getElementById('home-name');

    function renderDistance() {
        if (distKm) distKm.textContent = totalDistance(home).toLocaleString('ko-KR');
        if (homeName) homeName.textContent = home;
    }

    var homeModal = document.getElementById('home-modal');
    var homeList = document.getElementById('home-list');
    var homeBtn = document.getElementById('home-btn');
    var homePending = home;

    if (homeBtn && homeModal && homeList) {
        homeBtn.addEventListener('click', function () {
            homePending = home;
            homeList.innerHTML = HOME_OPTIONS.map(function (h) {
                return '<div class="m-opt' + (h === home ? ' sel' : '') + '" data-home="' + h + '">' +
                    '<span class="mo-ic">🏠</span><span class="mo-name">' + h + '</span></div>';
            }).join('');
            homeModal.classList.add('show');
        });
        homeList.addEventListener('click', function (e) {
            var o = e.target.closest('.m-opt');
            if (!o) return;
            homePending = o.dataset.home;
            [].forEach.call(homeList.children, function (c) { c.classList.toggle('sel', c === o); });
        });
        document.getElementById('home-cancel').addEventListener('click', function () { homeModal.classList.remove('show'); });
        homeModal.addEventListener('click', function (e) { if (e.target === homeModal) homeModal.classList.remove('show'); });
        document.getElementById('home-confirm').addEventListener('click', function () {
            home = homePending; saveHome(home);
            homeModal.classList.remove('show');
            renderDistance();
        });
    }
    renderDistance();

    /* =========================== 칭호 =========================== */

    /* 칭호 목록은 서버(titles 테이블)가 정본이다.
     * v17 이전에는 38종 카탈로그를 이 파일에 하드코딩하고 보유 여부만 대조했는데,
     * 이제 DB에 38종이 카테고리까지 들어있어서 화면이 서버 데이터를 그대로 그린다.
     * 템플릿이 #titles-data 에 data-* 로 내려주고, 순서(카테고리 정렬)도 서버가 정한다.
     */
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

    var vMy = document.getElementById('view-mypage');
    var vT = document.getElementById('view-titles');
    var btnMore = document.getElementById('btn-titles-more');
    var btnBack = document.getElementById('btn-titles-back');
    if (!vMy || !vT || !btnMore) return;

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
        document.getElementById('tgrid').innerHTML = html;
    }

    btnMore.addEventListener('click', function () {
        vMy.hidden = true; vT.hidden = false;
        renderRepCard(); renderTGrid();
        window.scrollTo(0, 0);
    });
    if (btnBack) btnBack.addEventListener('click', function () {
        vT.hidden = true; vMy.hidden = false;
        window.scrollTo(0, 0);
    });

    // 칭호 호버 시 조건 툴팁 - 잠긴 칭호는 해금 조건, 이미 딴 칭호는 달성 조건을 보여준다.
    // (원 목업은 잠긴 카드에만 data-cond 를 심어놔서 딴 칭호는 호버해도 아무것도 안 떴다)
    var tip = document.getElementById('tc-tip'), tg = document.getElementById('tgrid');
    if (tip && tg) {
        tg.addEventListener('mouseover', function (e) {
            var c = e.target.closest('.tcard');
            if (!c || !c.dataset.cond) return;
            tip.textContent = (c.classList.contains('locked') ? '🔒 ' : '✅ ') + c.dataset.cond;
            tip.classList.add('show');
        });
        tg.addEventListener('mousemove', function (e) {
            var c = e.target.closest('.tcard');
            if (!c || !c.dataset.cond) { tip.classList.remove('show'); return; }
            tip.style.left = (e.clientX - tip.offsetWidth / 2) + 'px';
            tip.style.top = (e.clientY - tip.offsetHeight - 12) + 'px';
        });
        tg.addEventListener('mouseout', function (e) {
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
            // 프로필 이름 옆 대표 칭호 칩도 같이 갱신(🏷️ 는 형제 span 이라 이름만 넣는다)
            var chip = document.getElementById('title-text');
            if (chip && rep) chip.textContent = rep.name;
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

/**
 * 마이페이지 "나의 여행 지도" 렌더링. 담당: 김민규(⑥).
 *
 * 지도 목업(아티팩트) 로직을 실제 코드로 이식한 버전.
 * 순수 DOM+SVG, D3 런타임 불필요. 지역 경계 데이터는 무겁고 자주 안 바뀌므로
 * /assets/geo/regions.json 에 정적 파일로 저장해두고 fetch로 받아 쓴다
 * (아티팩트의 window.REGION_DATA를 그대로 추출한 것 - mapshaper 단순화 + d3-geo 투영 +
 * polylabel 라벨점까지 이미 계산되어 있어 재계산이 필요 없다).
 *
 * regions.json에는 지오메트리(d, key, name, lx, ly, drillable, inset)만 들어있고
 * 방문 횟수/일수 같은 사용자 데이터는 없다. 색과 수치는 서버(TravelHeatmapService)가
 * 내려준 window.MYPAGE_HEATMAP.regions(지역명 -> {trips, days, score})를 지오메트리에
 * 얹어서 계산한다.
 *
 * ※ 지역 계층 불일치(중요, Phase 2 팀 협의 필요):
 *   - 한국: regions.json의 오버뷰가 17개 시도 평면이라 서버 지역명과 거의 그대로 맞는다.
 *   - 일본: regions.json은 지방(8권역+오키나와) -> 현 2단계인데, 서버는 지금 현 단위
 *     한글 지명만 갖고 있다. 그래서 현 단위로 서버 데이터를 매칭한 뒤, 지방(오버뷰) 단계는
 *     그 지방에 속한 현들의 합계로 집계해서 색을 칠한다. 지역명이 REGION_ALIASES에
 *     없으면 어느 현/시도에도 못 얹히니 콘솔에 경고만 남기고 조용히 넘어간다.
 *   - 서버 지역명 체계가 앞으로 바뀌면(예: enum 도입) 여기 NAME_ALIASES만 고치면 된다.
 *
 * ※ 스냅(사진): 지역 태그가 붙은 내 게시글(posts.region)을 지역별로 묶어, 마우스를 올린
 *   지역 위에 띄운다. 별도 API 없이 템플릿이 #snaps-data 로 내려준 것을 읽는다.
 *   일본 오버뷰(지방)에서는 그 지방에 속한 현들의 스냅을 합쳐서 보여준다.
 *
 * ※ 클릭 버그 교훈: SVG 지역을 hover 시 appendChild로 맨 위로 올리면 실제 마우스 클릭
 *   이벤트가 깨진다. 그래서 활성 지역은 복제본을 최상단 레이어(g-active)에 미리 만들어두고
 *   표시만 토글한다. 클릭은 stage 레벨에서 마지막 호버 지역(focused)으로 드릴한다.
 */
(function () {
    'use strict';

    var REGIONS_URL = '/assets/geo/regions.json';

    // 여행 지수 -> 색 단계. mypage-heatmap-tiers.css 대신 index.html의 --tier-0~4 변수를 그대로 쓴다.
    // (TravelHeatmapService: 완료 여행 1건당 3점 + 여행 하루당 1점, 기준을 바꾸면 여기도 같이 고칠 것)
    var TIERS = [
        { min: 26, color: 'var(--tier-4)', label: '단골' },
        { min: 16, color: 'var(--tier-3)', label: '자주' },
        { min: 8, color: 'var(--tier-2)', label: '여러 번' },
        { min: 1, color: 'var(--tier-1)', label: '가끔' },
        { min: 0, color: 'var(--tier-0)', label: '아직 안 가봄' }
    ];

    // 서버 지역명이 map-data.js 시절 표기(광역시/도 단위 등)와 다를 수 있어 별칭을 둔다.
    // 지도 쪽 이름과 서버 쪽 이름이 늘어나면 여기만 고치면 된다.
    var NAME_ALIASES = {
        '서울특별시': '서울', '경기도': '경기', '인천광역시': '인천',
        '강원특별자치도': '강원', '강원도': '강원',
        '충청북도': '충북', '충청남도': '충남', '세종특별자치시': '세종',
        '전라북도': '전북', '전북특별자치도': '전북', '전라남도': '전남',
        '경상북도': '경북', '경상남도': '경남',
        '대구광역시': '대구', '부산광역시': '부산', '광주광역시': '광주',
        '대전광역시': '대전', '울산광역시': '울산', '제주특별자치도': '제주', '제주도': '제주'
    };

    function tier(v) {
        for (var i = 0; i < TIERS.length; i++) {
            if (v >= TIERS[i].min) return TIERS[i];
        }
        return TIERS[TIERS.length - 1];
    }

    /* 지역명 -> 그 지역에서 찍은 스냅 목록. 템플릿의 #snaps-data 에서 읽는다. */
    var snapsByRegion = (function () {
        var map = {};
        [].forEach.call(document.querySelectorAll('#snaps-data span'), function (el) {
            var raw = (el.dataset.region || '').trim();
            if (!raw) return;
            var name = NAME_ALIASES[raw] || raw;
            (map[name] = map[name] || []).push({
                thumb: el.dataset.thumb || '',
                title: el.dataset.title || ''
            });
        });
        return map;
    })();

    /** 이 지역에서 보여줄 스냅들. 일본 오버뷰(지방)면 소속 현들의 스냅을 합친다. */
    function snapsFor(r) {
        if (state.level === 'overview' && state.country === 'japan'
                && DATA.japan.drill && DATA.japan.drill[r.key]) {
            var merged = [];
            DATA.japan.drill[r.key].forEach(function (prefecture) {
                var list = snapsByRegion[prefecture.name];
                if (list) merged = merged.concat(list);
            });
            return merged;
        }
        return snapsByRegion[r.name] || [];
    }

    var NS = 'http://www.w3.org/2000/svg';

    var svg = document.getElementById('map');
    if (!svg) return; // 지도 섹션이 없는 페이지에서는 아무것도 하지 않는다.

    var gD = document.getElementById('g-deco'), gP = document.getElementById('g-paths'),
        gA = document.getElementById('g-active'), gL = document.getElementById('g-labels');
    var stage = document.getElementById('stage'), snapsEl = document.getElementById('snaps'),
        readout = document.getElementById('readout'), mapStat = document.getElementById('map-stat'),
        backBtn = document.getElementById('map-back');
    var toggleWrap = document.getElementById('map-toggle'), btnJp = document.getElementById('btn-map-jp'),
        btnKr = document.getElementById('btn-map-kr');

    var DATA = null;
    var state = { country: 'japan', level: 'overview', region: null };
    var focused = null;

    /** 서버 raw(지역명 -> {trips, days, score})를 정규화한 이름으로 다시 색인한다. */
    function normalizeRaw(raw) {
        var out = {};
        Object.keys(raw || {}).forEach(function (name) {
            var norm = NAME_ALIASES[name] || name;
            var cell = raw[name];
            if (!out[norm]) out[norm] = { trips: 0, days: 0, score: 0 };
            out[norm].trips += cell.trips || 0;
            out[norm].days += cell.days || 0;
            out[norm].score += cell.score || 0;
        });
        return out;
    }

    /** regions.json 지오메트리에 서버 통계를 얹는다. 매칭 안 되는 지역은 0으로 둔다. */
    function attachStats(list, byName, matchedNames) {
        list.forEach(function (r) {
            var cell = byName[r.name];
            if (cell) {
                r.trips = cell.trips; r.days = cell.days; r.score = cell.score;
                matchedNames.delete(r.name);
            } else {
                r.trips = 0; r.days = 0; r.score = 0;
            }
        });
    }

    /** 일본 지방(오버뷰) 값은 그 지방에 속한 현들의 합계로 만든다. */
    function rollUpJapanOverview() {
        var drill = DATA.japan.drill;
        DATA.japan.overview.forEach(function (region) {
            var prefs = drill[region.key];
            if (!prefs || !prefs.length) { region.trips = 0; region.days = 0; region.score = 0; return; }
            var t = 0, d = 0, s = 0;
            prefs.forEach(function (p) { t += p.trips || 0; d += p.days || 0; s += p.score || 0; });
            region.trips = t; region.days = d; region.score = s;
        });
    }

    function mergeServerData() {
        var raw = (window.MYPAGE_HEATMAP && window.MYPAGE_HEATMAP.regions) || {};
        var byName = normalizeRaw(raw);
        var matchedNames = new Set(Object.keys(byName));

        attachStats(DATA.korea.overview, byName, matchedNames);
        Object.keys(DATA.japan.drill).forEach(function (key) {
            attachStats(DATA.japan.drill[key], byName, matchedNames);
        });
        // 한국도 드릴(시/군/구) 데이터가 생기면 색을 칠해야 한다. 지금은 서버가 시/도
        // 단위 지역명만 내려주므로 전부 0(미방문)으로 붙지만, 이 루프가 없으면 r.trips 등이
        // undefined로 남아 tier() 비교에서 조용히 깨진다 - 드릴 데이터를 추가할 때마다
        // 매번 여기 고칠 필요 없게 미리 일반화해둔다.
        Object.keys(DATA.korea.drill).forEach(function (key) {
            attachStats(DATA.korea.drill[key], byName, matchedNames);
        });
        rollUpJapanOverview();

        if (matchedNames.size) {
            console.warn('[여행 지도] 지도에서 찾을 수 없는 지역명(서버 heatmap):', Array.from(matchedNames));
        }
    }

    function curSet() {
        var c = DATA[state.country];
        return state.level === 'overview' ? c.overview : c.drill[state.region];
    }
    function regionLabel() {
        if (state.level !== 'drill') return '';
        var o = DATA[state.country].overview.find(function (r) { return r.key === state.region; });
        return o ? o.name : '';
    }

    function draw() {
        var set = curSet(), fs = parseFloat(DATA.viewBox.split(/\s+/)[2]) * 0.017;
        gD.innerHTML = ''; gP.innerHTML = ''; gA.innerHTML = ''; gL.innerHTML = '';
        svg.classList.remove('focusing');

        set.forEach(function (r) {
            var p = document.createElementNS(NS, 'path');
            p.setAttribute('class', 'region'); p.setAttribute('d', r.d);
            p.setAttribute('fill', tier(r.score).color);
            p.setAttribute('tabindex', '0'); p.setAttribute('role', 'button');
            p.setAttribute('aria-label', r.name + (r.trips > 0 ? ' ' + r.days + '일' : ' 미방문') + (r.drillable ? ' (클릭하면 상세)' : ''));
            if (r.drillable) p.style.cursor = 'zoom-in';
            p.__r = r;
            gP.appendChild(p);
            if (!r.inset) {
                var ac = document.createElementNS(NS, 'path');
                ac.setAttribute('class', 'active-clone'); ac.setAttribute('d', r.d);
                ac.setAttribute('fill', tier(r.score).color); ac.setAttribute('id', 'ac-' + r.key);
                gA.appendChild(ac);
            }
        });

        // 오키나와 인셋(일본 오버뷰만)
        var c = DATA[state.country];
        if (state.level === 'overview' && c.inset && state.country === 'japan') {
            var ins = c.inset, x = ins[0][0] - 6, y = ins[0][1] - 6, w = ins[1][0] - ins[0][0] + 12, h = ins[1][1] - ins[0][1] + 12;
            var rect = document.createElementNS(NS, 'rect');
            rect.setAttribute('class', 'inset-frame'); rect.setAttribute('x', x); rect.setAttribute('y', y);
            rect.setAttribute('width', w); rect.setAttribute('height', h); rect.setAttribute('rx', '10');
            gD.appendChild(rect);
            var cap = document.createElementNS(NS, 'text');
            cap.setAttribute('class', 'inset-cap'); cap.setAttribute('x', x + w / 2); cap.setAttribute('y', y - 6);
            cap.setAttribute('font-size', fs * 0.85); cap.textContent = '오키나와';
            gD.appendChild(cap);
        }

        set.forEach(function (r) {
            if (r.inset) return;
            var t = document.createElementNS(NS, 'text');
            t.setAttribute('class', 'rlabel'); t.setAttribute('font-size', fs);
            t.setAttribute('id', 'lbl-' + r.key); t.textContent = r.name;
            gL.appendChild(t);
        });
        set.forEach(function (r) {
            if (r.inset) return;
            var lbl = document.getElementById('lbl-' + r.key);
            if (lbl && r.lx != null) { lbl.setAttribute('x', r.lx); lbl.setAttribute('y', r.ly + fs * 0.34); }
        });

        var vis = set.filter(function (r) { return r.trips > 0; });
        var td = vis.reduce(function (s, r) { return s + r.days; }, 0);
        var tt = vis.reduce(function (s, r) { return s + r.trips; }, 0);
        if (state.level === 'drill') {
            mapStat.innerHTML = '<span class="map-crumb"><b>' + regionLabel() + '</b> · ' + tt + '회 · ' + td + '일</span>';
            backBtn.hidden = false;
        } else {
            mapStat.innerHTML = '<b>' + tt + '</b>번의 여행 · <b>' + vis.length + '</b>곳 · 총 <b>' + td + '</b>일';
            backBtn.hidden = true;
        }
        clearFocus();
    }
    function swapDraw() {
        svg.classList.add('swapping');
        setTimeout(function () { draw(); svg.classList.remove('swapping'); }, 180);
    }

    function focus(pathEl) {
        var r = pathEl.__r; focused = r; svg.classList.add('focusing');
        [].forEach.call(gA.querySelectorAll('.active-clone.on'), function (el) { el.classList.remove('on'); });
        var ac = document.getElementById('ac-' + r.key); if (ac) ac.classList.add('on');
        [].forEach.call(gL.querySelectorAll('.rlabel.show'), function (el) { el.classList.remove('show'); });
        var lbl = document.getElementById('lbl-' + r.key); if (lbl) lbl.classList.add('show');

        var suffix = (state.level === 'overview' && r.drillable) ? ' <span style="font-size:12px;color:var(--sash-deep);">· 클릭해서 자세히 보기</span>' : '';
        if (r.trips > 0) {
            var n = Math.max(1, r.days - 1);
            var sc = snapsFor(r).length;
            var scTxt = sc > 0 ? (' · 스냅 ' + sc) : '';
            readout.innerHTML = '<span class="pip" style="background:' + tier(r.score).color + '"></span>' +
                '<span class="msg"><b>' + r.name + '</b> — ' + n + '박 ' + r.days + '일 즐겼습니다!' + scTxt + suffix + '</span>';
        } else {
            readout.innerHTML = '<span class="pip" style="background:var(--tier-0)"></span>' +
                '<span class="msg"><b>' + r.name + '</b> — 아직 다녀오지 않았어요' + suffix + '</span>';
        }
        showSnaps(pathEl, r);
    }
    function clearFocus() {
        focused = null; svg.classList.remove('focusing');
        [].forEach.call(gA.querySelectorAll('.active-clone.on'), function (el) { el.classList.remove('on'); });
        [].forEach.call(gL.querySelectorAll('.rlabel.show'), function (el) { el.classList.remove('show'); });
        var hint = state.level === 'drill'
            ? '현에 마우스를 올리면 그 현의 방문 기록이 떠요'
            : (DATA[state.country].overview.some(function (x) { return x.drillable; })
                ? '🔍 지역권을 클릭하면 그 안의 현을 볼 수 있어요'
                : '지역에 마우스를 올리면 확대되며 방문 기록이 떠요');
        readout.innerHTML = '<span class="msg" style="color:var(--ink-soft)">' + hint + '</span>';
        hideSnaps();
    }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    /**
     * 스냅 하나를 그린다.
     * thumbnailUrl 은 실제 업로드 경로(/uploads/...)이거나 자리표시 클래스명(ph1~ph4)이다
     * - 피드(mypage/index.html)가 쓰는 규칙과 같게 맞춘다.
     */
    function pokeHtml(snap, i, x, y, delay) {
        var thumb = snap.thumb || '';
        var uploaded = thumb.indexOf('/uploads/') === 0;
        var shade = /^ph[1-4]$/.test(thumb) ? 's' + thumb.charAt(2) : 's' + ((i % 4) + 1);
        var bg = uploaded ? "background-image:url('" + encodeURI(thumb) + "');" : '';
        return '<div class="snap-poke ' + shade + '" title="' + esc(snap.title) + '"' +
            ' style="' + bg + 'left:' + x + 'px;top:' + y + 'px;animation-delay:' + delay + 'ms"></div>';
    }

    function showSnaps(pathEl, r) {
        var MAXN = 10;                                  // 너무 많으면 최신 것만(겹침 허용)
        var snaps = snapsFor(r);
        var n = Math.min(snaps.length, MAXN);
        snapsEl.classList.remove('show'); void snapsEl.offsetWidth;
        if (n === 0) { snapsEl.innerHTML = ''; return; }  // 스냅 없으면 포커싱만

        var box = pathEl.getBoundingClientRect(), sb = stage.getBoundingClientRect();
        var cx = box.left - sb.left + box.width / 2, cy = box.top - sb.top + box.height / 2;
        var HALF = 59, rad = Math.max(box.width, box.height) / 2, html = '';
        if (n >= 5) {
            // 스냅이 많으면 권역을 빙 둘러 원형 배치(조금 겹쳐도 OK)
            var R = rad + HALF + 54;
            for (var i = 0; i < n; i++) {
                var ang = -Math.PI / 2 + (i / n) * Math.PI * 2;   // 위에서 시작해 시계방향
                var x = cx + Math.cos(ang) * R - HALF, y = cy + Math.sin(ang) * R - HALF;
                x = Math.max(6, Math.min(x, sb.width - 118 - 6)); y = Math.max(6, Math.min(y, sb.height - 118 - 6));
                html += pokeHtml(snaps[i], i, x, y, i * 45);
            }
        } else {
            // 적으면 여유 넓은 한쪽 변을 따라 원호(부챗살)
            var R2 = rad + HALF + 54;
            var leftRoom = box.left - sb.left, rightRoom = sb.width - (box.right - sb.left);
            var center = leftRoom >= rightRoom ? Math.PI : 0;
            var span = n <= 1 ? 0 : Math.min(2.5, 0.55 * (n - 1));
            for (var j = 0; j < n; j++) {
                var a = center - span / 2 + (n <= 1 ? 0 : span * j / (n - 1));
                var px = cx + Math.cos(a) * R2 - HALF, py = cy + Math.sin(a) * R2 - HALF;
                px = Math.max(6, Math.min(px, sb.width - 118 - 6)); py = Math.max(6, Math.min(py, sb.height - 118 - 6));
                html += pokeHtml(snaps[j], j, px, py, j * 55);
            }
        }
        snapsEl.innerHTML = html; snapsEl.classList.add('show');
    }

    function hideSnaps() { snapsEl.classList.remove('show'); }

    function drillInto(r) {
        if (!r) return;
        if (state.level === 'overview' && r.drillable && DATA[state.country].drill && DATA[state.country].drill[r.key]) {
            state.level = 'drill'; state.region = r.key; focused = null; hideSnaps(); swapDraw();
        }
    }

    svg.addEventListener('mousemove', function (e) {
        var t = e.target.closest('.region');
        if (t && t.__r !== focused) focus(t);
    });
    svg.addEventListener('mouseleave', clearFocus);
    svg.addEventListener('focusin', function (e) { var t = e.target.closest('.region'); if (t) focus(t); });
    stage.addEventListener('click', function (e) {
        if (e.target.closest('#map-back') || e.target.closest('.map-toggle')) return;
        if (state.level === 'overview') drillInto(focused);
    });
    backBtn.addEventListener('click', function () { state.level = 'overview'; state.region = null; hideSnaps(); swapDraw(); });
    window.addEventListener('resize', function () { hideSnaps(); });

    function setCountry(country) {
        state.country = country; state.level = 'overview'; state.region = null;
        if (toggleWrap) toggleWrap.classList.toggle('kr-active', country === 'korea');
        if (btnJp) btnJp.classList.toggle('on', country === 'japan');
        if (btnKr) btnKr.classList.toggle('on', country === 'korea');
        draw();
    }
    if (btnJp) btnJp.addEventListener('click', function () { setCountry('japan'); });
    if (btnKr) btnKr.addEventListener('click', function () { setCountry('korea'); });

    fetch(REGIONS_URL)
        .then(function (res) {
            if (!res.ok) throw new Error('regions.json HTTP ' + res.status);
            return res.json();
        })
        .then(function (json) {
            DATA = json;
            svg.setAttribute('viewBox', DATA.viewBox);
            mergeServerData();
            draw();
        })
        .catch(function (err) {
            console.error('[여행 지도] 지역 경계 데이터를 불러오지 못했습니다:', err);
            if (mapStat) mapStat.textContent = '지도를 불러오지 못했습니다.';
        });
})();

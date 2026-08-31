/**
 * 마이페이지 "지도 정복 히트맵" 렌더링. 담당: 김민규(⑥).
 *
 * 서버(TravelHeatmapService)가 내려준 지역별 집계를 map-data.js 의 지역 좌표에 얹어
 * 색 단계로 칠한다. 서버는 지역명을 한글 그대로 보내고, 지도 키(osaka / capital 등)로의
 * 변환은 여기서 map-data.js 의 name 을 보고 처리한다 - 지역이 늘어도 자바를 안 고치려고.
 *
 * 색 단계 기준은 "여행 지수" = 완료 여행 1건당 3점 + 여행 하루당 1점.
 * 기준을 바꾸려면 아래 TIERS 와 TravelHeatmapService 의 상수를 같이 고칠 것.
 *
 * ※ map-data.js 에는 지역별 SVG 경로(d)나 폴리곤(poly) 데이터가 없고 중심 좌표(cx, cy)만
 *   있다. 그래서 지역을 면으로 칠하지 않고 중심점 마커로 표시한다. 실제 행정구역 경계를
 *   칠하려면 map-data.js 에 경로 데이터가 추가돼야 한다(공용 에셋이라 팀 협의 필요).
 */
(function () {
    'use strict';

    // 지수 -> 색 단계. 위에서부터 먼저 맞는 것을 쓴다.
    var TIERS = [
        { min: 26, color: 'var(--tier-4)', label: '단골' },
        { min: 16, color: 'var(--tier-3)', label: '자주' },
        { min: 8, color: 'var(--tier-2)', label: '여러 번' },
        { min: 1, color: 'var(--tier-1)', label: '가끔' },
        { min: 0, color: 'var(--tier-0)', label: '아직 안 가봄' }
    ];

    // map-data.js 의 지역명과 다르게 저장될 수 있는 표기들.
    var REGION_ALIASES = {
        '서울': 'capital', '경기': 'capital', '인천': 'capital', '서울특별시': 'capital',
        '강원도': 'gangwon', '제주도': 'jeju',
        '부산': 'gyeongnam', '울산': 'gyeongnam', '경상남도': 'gyeongnam',
        '대구': 'gyeongbuk', '경상북도': 'gyeongbuk',
        '대전': 'chungnam', '세종': 'chungnam', '충청남도': 'chungnam', '충청북도': 'chungbuk',
        '광주': 'jeonnam', '전라남도': 'jeonnam', '전라북도': 'jeonbuk'
    };

    var hasKorea = typeof KOREA_REGIONS !== 'undefined';
    var hasJapan = typeof JAPAN_REGIONS !== 'undefined';
    if (!hasKorea && !hasJapan) return;

    var raw = (window.MYPAGE_HEATMAP && window.MYPAGE_HEATMAP.regions) || {};

    /** 지역명 -> 지도 키 색인을 map-data.js 에서 만든다. */
    function buildNameIndex() {
        var index = {};
        [hasKorea ? KOREA_REGIONS : {}, hasJapan ? JAPAN_REGIONS : {}].forEach(function (set) {
            Object.keys(set).forEach(function (key) { index[set[key].name] = key; });
        });
        Object.keys(REGION_ALIASES).forEach(function (name) { index[name] = REGION_ALIASES[name]; });
        return index;
    }

    var nameToKey = buildNameIndex();

    /** 서버가 준 지역명 기준 집계를 지도 키 기준으로 옮긴다. */
    var byKey = {};
    Object.keys(raw).forEach(function (regionName) {
        var key = nameToKey[regionName];
        if (!key) {
            // 지도에 없는 지역명. 조용히 넘기되, 왜 안 칠해지는지 알 수 있게 로그는 남긴다.
            console.warn('[히트맵] 지도에서 찾을 수 없는 지역명:', regionName);
            return;
        }
        var cell = raw[regionName];
        if (!byKey[key]) byKey[key] = { trips: 0, days: 0, score: 0 };
        byKey[key].trips += cell.trips || 0;
        byKey[key].days += cell.days || 0;
        byKey[key].score += cell.score || 0;
    });

    function statsOf(key) {
        return byKey[key] || { trips: 0, days: 0, score: 0 };
    }

    function tierOf(score) {
        for (var i = 0; i < TIERS.length; i++) {
            if (score >= TIERS[i].min) return TIERS[i];
        }
        return TIERS[TIERS.length - 1];
    }

    var tip = document.getElementById('region-tip');

    function describe(name, stats) {
        if (!tip) return;
        if (stats.trips === 0) {
            tip.innerHTML = '<b>' + name + '</b> — 아직 다녀오지 않았어요';
            return;
        }
        tip.innerHTML = '<b>' + name + '</b> — 완료 여행 ' + stats.trips + '회 · ' + stats.days + '일';
    }

    /** 마커 하나에 마우스/키보드 이벤트를 붙인다. */
    function bind(el, name, stats) {
        var show = function () { describe(name, stats); };
        el.addEventListener('click', show);
        el.addEventListener('mouseenter', show);
        el.addEventListener('focus', show);
        el.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') show(); });
    }

    var SVG_NS = 'http://www.w3.org/2000/svg';

    function marker(cx, cy, radius, stats, name, idPrefix, key) {
        var circle = document.createElementNS(SVG_NS, 'circle');
        circle.setAttribute('class', 'region-dot');
        circle.setAttribute('id', idPrefix + '-' + key);
        circle.setAttribute('cx', cx);
        circle.setAttribute('cy', cy);
        circle.setAttribute('r', stats.trips > 0 ? radius * 1.35 : radius);
        circle.setAttribute('fill', tierOf(stats.score).color);
        circle.setAttribute('tabindex', '0');
        circle.setAttribute('role', 'button');
        circle.setAttribute('aria-label', name + ' 완료 여행 ' + stats.trips + '회');
        return circle;
    }

    function drawJapan() {
        var svg = document.getElementById('map-jp');
        if (!svg || !hasJapan) return;
        svg.innerHTML = '';
        Object.keys(JAPAN_REGIONS).forEach(function (key) {
            var region = JAPAN_REGIONS[key];
            var stats = statsOf(key);
            var dot = marker(region.cx, region.cy, 1.15, stats, region.name, 'jp', key);
            svg.appendChild(dot);
            bind(dot, region.name, stats);
        });
    }

    function drawKorea() {
        var svg = document.getElementById('map-kr');
        if (!svg || !hasKorea) return;
        svg.innerHTML = '';
        Object.keys(KOREA_REGIONS).forEach(function (key) {
            var region = KOREA_REGIONS[key];
            var stats = statsOf(key);

            var dot = marker(region.cx, region.cy, 2.6, stats, region.name, 'kr', key);
            svg.appendChild(dot);
            bind(dot, region.name, stats);

            var label = document.createElementNS(SVG_NS, 'text');
            label.setAttribute('class', 'region-label');
            label.setAttribute('x', region.cx);
            label.setAttribute('y', region.cy + 8);
            label.setAttribute('font-size', '4.5');
            label.textContent = region.name;
            svg.appendChild(label);
        });
    }

    drawJapan();
    drawKorea();

    // 한국/일본 지도 전환
    function setMapMode(mode) {
        var toggle = document.getElementById('map-toggle');
        if (!toggle) return;
        toggle.classList.toggle('kr-active', mode === 'kr');
        document.getElementById('btn-map-jp').classList.toggle('on', mode === 'jp');
        document.getElementById('btn-map-kr').classList.toggle('on', mode === 'kr');
        document.getElementById('frame-jp').style.display = mode === 'jp' ? '' : 'none';
        document.getElementById('frame-kr').style.display = mode === 'kr' ? '' : 'none';
        if (tip) tip.textContent = '지역을 눌러보세요';
    }

    var btnJp = document.getElementById('btn-map-jp');
    var btnKr = document.getElementById('btn-map-kr');
    if (btnJp) btnJp.addEventListener('click', function () { setMapMode('jp'); });
    if (btnKr) btnKr.addEventListener('click', function () { setMapMode('kr'); });
})();

/**
 * 일본어 모드(?lang=ja / TANOSHIMI_LANG 쿠키) 화면 번역.
 *
 * 템플릿/JS 에 한국어로 직접 박힌 문구를 i18n-ja-dict.js 사전(window.TANOSHIMI_JA)으로 바꾼다.
 * 사전은 tools/i18n/build_ja_dict.py 가 소스에서 문구를 뽑아 Gemini 로 미리 번역해 둔 것 -
 * 런타임에 API 호출은 없다. 나중에 JS 로 그려지는 화면(MutationObserver)과 alert/confirm 도 처리.
 *
 * 매칭 순서: ① 문구 그대로 ② "{0}명" 같은 패턴 ③ 노드 안의 한국어 조각이 "전부" 사전에 있을 때만 조각별 치환.
 * ③을 "전부"로 제한한 이유: 파티 제목·채팅 같은 사용자 글이 반쪽만 번역돼 섞이지 않게(사용자 글은 번역 버튼 담당).
 */
(function () {
    'use strict';
    var DICT = window.TANOSHIMI_JA || {};
    var KO = /[가-힣]/;
    var ATTRS = ['placeholder', 'title', 'alt', 'aria-label'];
    var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, CODE: 1, PRE: 1 };

    document.documentElement.lang = 'ja';

    // "{0}명" -> /^(.+?)명$/ 패턴 목록 (자리표시자가 있는 키만)
    var patterns = [];
    Object.keys(DICT).forEach(function (k) {
        if (!/\{\d+\}/.test(k)) return;
        var order = [];
        var src = k.split(/(\{\d+\})/).map(function (part) {
            var m = /^\{(\d+)\}$/.exec(part);
            if (m) { order.push(m[1]); return '([\\s\\S]+?)'; }
            return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        }).join('');
        // 고정 문구가 충분히 구체적이면(한글 4자 이상, 예: "{0} 프로필 보기") {0} 에 닉네임 같은 번역 불가 값도 허용
        var specific = (k.replace(/\{\d+\}/g, '').match(/[가-힣]/g) || []).length >= 4;
        patterns.push({ re: new RegExp('^' + src + '$'), order: order, ja: DICT[k], specific: specific });
    });
    // 긴(구체적인) 패턴 먼저
    patterns.sort(function (a, b) { return b.re.source.length - a.re.source.length; });

    function lookup(text) {
        if (Object.prototype.hasOwnProperty.call(DICT, text)) return DICT[text];
        for (var i = 0; i < patterns.length; i++) {
            var p = patterns[i], m = p.re.exec(text);
            if (!m) continue;
            // {0} 자리에 들어간 값에 한국어가 있으면 그 값도 번역해서 넣는다("👫 교토 모집 중인 파티" 의 교토→京都).
            // 번역이 안 되는 값이면 "{0}명" 같은 짧은 패턴은 사용자 글일 수 있으니 버리고,
            // 구체적인 패턴("{0} 프로필 보기")만 원래 값(닉네임 등) 그대로 넣는다.
            var vals = [], ok = true;
            for (var g = 1; g < m.length && ok; g++) {
                var v = m[g];
                if (KO.test(v)) {
                    var tv = translateCore(v.trim());
                    if (tv !== null) v = tv; else ok = p.specific;
                }
                vals.push(v);
            }
            if (!ok) continue;
            var out = p.ja;
            p.order.forEach(function (n, idx) { out = out.split('{' + n + '}').join(vals[idx]); });
            return out;
        }
        return null;
    }

    // 서버가 찍는 날짜/시간 표기 (#temporals 'M월 d일' 등)
    function localizeDates(s) {
        return s.replace(/(\d+)\s*년/g, '$1年').replace(/(\d+)\s*월/g, '$1月')
                .replace(/(\d+)\s*일차/g, '$1日目').replace(/(\d+)\s*일/g, '$1日')
                .replace(/(\d+)\s*시간/g, '$1時間').replace(/(\d+)\s*분/g, '$1分')
                .replace(/(\d+)\s*인(?![가-힣])/g, '$1人').replace(/(\d+)\s*명(?![가-힣])/g, '$1名')
                .replace(/(\d+)\s*만\s*원/g, '$1万ウォン').replace(/(\d+)\s*원/g, '$1ウォン');
    }

    function translateCore(core) {
        var hit = lookup(core);
        if (hit !== null) return hit;
        core = localizeDates(core);
        if (!KO.test(core)) return core;
        hit = lookup(core);
        if (hit !== null) return hit;
        // ③ 한국어 조각(공백 포함 한글 덩어리)이 전부 사전에 있으면 조각별 치환
        var runs = core.match(/[가-힣][가-힣\s]*[가-힣]|[가-힣]/g) || [];
        if (!runs.length || !runs.every(function (r) { return Object.prototype.hasOwnProperty.call(DICT, r); })) return null;
        runs.sort(function (a, b) { return b.length - a.length; })
            .forEach(function (r) { core = core.split(r).join(DICT[r]); });
        return core;
    }

    function translate(s) {
        if (!s || !KO.test(s)) return null;
        var hit = translateCore(s.replace(/\s+/g, ' ').trim());
        if (hit === null) return null;
        // 앞뒤 공백은 원래대로 유지 (인라인 요소 사이 간격)
        var lead = /^\s*/.exec(s)[0], trail = /\s*$/.exec(s)[0];
        return lead + hit + trail;
    }
    window.tanoshimiT = function (s) { var t = translate(String(s)); return t === null ? s : t; };

    function translateNode(node) {
        if (node.nodeType === 3) {
            var p = node.parentNode;
            if (!p || SKIP[p.nodeName] || p.isContentEditable) return;
            var t = translate(node.nodeValue);
            if (t !== null && t !== node.nodeValue) node.nodeValue = t;
            return;
        }
        if (node.nodeType !== 1) return;
        // 속성(placeholder 등)은 textarea 에도 번역 - 내용(사용자 입력)만 건너뛴다
        ATTRS.forEach(function (a) {
            var v = node.getAttribute(a);
            var t = v && translate(v);
            if (t) node.setAttribute(a, t);
        });
        if (SKIP[node.nodeName]) return;
        if (node.nodeName === 'INPUT' && /^(button|submit|reset)$/i.test(node.type)) {
            var tv = translate(node.value);
            if (tv) node.value = tv;
        }
        for (var c = node.firstChild; c; c = c.nextSibling) translateNode(c);
    }

    ['alert', 'confirm', 'prompt'].forEach(function (fn) {
        var orig = window[fn];
        window[fn] = function (msg) {
            var args = Array.prototype.slice.call(arguments);
            if (typeof msg === 'string') args[0] = msg.split('\n').map(window.tanoshimiT).join('\n');
            return orig.apply(window, args);
        };
    });

    function run() {
        var t = translate(document.title);
        if (t) document.title = t;
        if (document.body) translateNode(document.body);
        document.documentElement.classList.remove('i18n-pending');
        new MutationObserver(function (list) {
            list.forEach(function (m) {
                if (m.type === 'childList') m.addedNodes.forEach(translateNode);
                else translateNode(m.target);
            });
        }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
})();

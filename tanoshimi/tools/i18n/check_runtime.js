// i18n-ja.js 매칭 규칙 자체 점검:  node tools/i18n/check_runtime.js
// 실제 사전(i18n-ja-dict.js)을 읽어 브라우저 없이 window.tanoshimiT 만 돌려본다.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const js = (f) => fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/js', f), 'utf8');
global.window = global;
global.alert = global.confirm = global.prompt = () => {};
global.MutationObserver = class { observe() {} };
global.document = { readyState: 'complete', title: '', body: null, documentElement: { classList: { remove() {} } } };
eval(js('i18n-ja-dict.js'));
eval(js('i18n-ja.js'));
const T = window.tanoshimiT;

assert.strictEqual(T('마이페이지'), 'マイページ');                                  // ① 그대로
assert.strictEqual(T('  마이페이지 \n'), '  マイページ \n');                       // 앞뒤 공백 유지
assert.ok(!/[가-힣]/.test(T('찬성 3 · 반대 1')), T('찬성 3 · 반대 1'));           // ② 패턴
assert.ok(T('👫 교토 모집 중인 파티 2개 보기').includes('京都'));                  // 패턴 값도 번역
assert.ok(T('유자차 프로필 보기').startsWith('유자차'));                            // 구체적 패턴: 닉네임은 그대로
assert.ok(!/[가-힣]/.test(T('1월 1일 출발')), T('1월 1일 출발'));                  // 날짜 + 조각
assert.strictEqual(T('오사카 신년 하츠모데 & 도톤보리 야경 같이 가요'),
                   '오사카 신년 하츠모데 & 도톤보리 야경 같이 가요');                // 사용자 글은 건드리지 않음
assert.strictEqual(T('우리는 4명'), '우리는 4명');                                // 짧은 패턴이 사용자 글을 먹지 않음
assert.strictEqual(T('Hello'), 'Hello');
console.log('i18n runtime OK -', Object.keys(window.TANOSHIMI_JA).length, 'entries');

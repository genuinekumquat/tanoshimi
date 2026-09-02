/**
 * [account-settings 신규] 계정 관리(/mypage/account) 전용. 담당: 김민규(⑥).
 *
 * 왼쪽 세로 탭 5개(회원정보조회/소셜 연동/알림 설정/계정 공개범위/차단 계정 관리)를
 * 한 페이지 안에서 JS로 전환한다 - 서버는 GET /mypage/account 한 번만 렌더링하고,
 * 저장은 전부 /api/mypage/account/* PUT/POST(AccountSettingsController), 차단 해제만
 * 기존 /api/block/{id} DELETE(BlockController)를 그대로 재사용한다.
 *
 * CSRF 는 csrf.js 의 window.api 공용 fetch 래퍼를 그대로 쓴다(직접 메타태그를 읽지 않는다).
 */
(function () {
    'use strict';

    var dataEl = document.getElementById('account-data');
    var ds = dataEl ? dataEl.dataset : {};

    // ===================== 탭 전환 =====================
    var tabButtons = document.querySelectorAll('.acct-tabs button[data-tab]');
    var panels = document.querySelectorAll('.acct-panel[data-panel]');

    function showTab(name) {
        tabButtons.forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.tab === name);
        });
        panels.forEach(function (panel) {
            panel.hidden = panel.dataset.panel !== name;
        });
    }
    tabButtons.forEach(function (btn) {
        btn.addEventListener('click', function () { showTab(btn.dataset.tab); });
    });

    // ===================== 토스트 =====================
    var toastEl = document.getElementById('toast');
    var toastTimer = null;
    function toast(message) {
        if (!toastEl) return;
        toastEl.textContent = message;
        toastEl.style.opacity = '1';
        toastEl.style.transform = 'translateX(-50%) translateY(0)';
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            toastEl.style.opacity = '0';
            toastEl.style.transform = 'translateX(-50%) translateY(20px)';
        }, 2200);
    }

    // ===================== 1. 회원정보조회 =====================
    var fEmail = document.getElementById('f-email');
    var fName = document.getElementById('f-name');
    var fPhone = document.getElementById('f-phone');
    var fGender = document.getElementById('f-gender');
    var fBirth = document.getElementById('f-birth');
    var fNationality = document.getElementById('f-nationality');
    var isSocial = ds.social === 'true';

    function fillProfileFields() {
        fEmail.value = ds.email || '';
        fName.value = ds.name || '';
        fPhone.value = ds.phone || '';
        fGender.value = ds.gender || 'male';
        fBirth.value = ds.birth || '';
        fNationality.value = ds.nationality || 'KR';
    }
    fillProfileFields();

    var btnEditInfo = document.getElementById('btn-edit-info');
    var editActions = document.getElementById('edit-actions');
    var btnSaveInfo = document.getElementById('btn-save-info');
    var btnCancelInfo = document.getElementById('btn-cancel-info');

    var pwModal = document.getElementById('pw-modal');
    var pwInput = document.getElementById('pw-input');
    var pwError = document.getElementById('pw-error');
    var pwCancel = document.getElementById('pw-cancel');
    var pwConfirm = document.getElementById('pw-confirm');
    var confirmedPassword = ''; // 서버가 updateProfile 에서 다시 검증하므로 여기 담아뒀다 같이 보낸다

    function enterEditMode() {
        [fName, fPhone, fBirth].forEach(function (el) { el.readOnly = false; });
        [fGender, fNationality].forEach(function (el) { el.disabled = false; });
        btnEditInfo.style.display = 'none';
        editActions.style.display = 'flex';
    }
    function exitEditMode() {
        [fName, fPhone, fBirth].forEach(function (el) { el.readOnly = true; });
        [fGender, fNationality].forEach(function (el) { el.disabled = true; });
        btnEditInfo.style.display = 'inline-flex';
        editActions.style.display = 'none';
        fillProfileFields();
    }

    btnEditInfo.addEventListener('click', function () {
        if (isSocial) {
            // 소셜 전용 계정은 알 수 없는 랜덤 해시라 비밀번호 확인을 건너뛴다
            // (AccountSettingsService.updateProfile 주석과 동일한 정책).
            confirmedPassword = '';
            enterEditMode();
            return;
        }
        pwError.style.display = 'none';
        pwInput.value = '';
        pwModal.classList.add('show');
        pwInput.focus();
    });

    pwCancel.addEventListener('click', function () { pwModal.classList.remove('show'); });

    pwConfirm.addEventListener('click', async function () {
        var pw = pwInput.value;
        if (!pw) { pwError.textContent = '비밀번호를 입력해 주세요.'; pwError.style.display = 'block'; return; }
        var result = await window.api.post('/api/mypage/account/verify-password', { password: pw });
        if (result.success && result.data === true) {
            confirmedPassword = pw;
            pwModal.classList.remove('show');
            enterEditMode();
        } else {
            pwError.textContent = result.message || '비밀번호가 일치하지 않습니다.';
            pwError.style.display = 'block';
        }
    });

    btnCancelInfo.addEventListener('click', exitEditMode);

    btnSaveInfo.addEventListener('click', async function () {
        var payload = {
            password: confirmedPassword,
            name: fName.value.trim(),
            phone: fPhone.value.trim(),
            gender: fGender.value,
            birthDate: fBirth.value,
            nationality: fNationality.value
        };
        var result = await window.api.put('/api/mypage/account/profile', payload);
        if (result.success) {
            ds.name = payload.name;
            ds.phone = payload.phone;
            ds.gender = payload.gender;
            ds.birth = payload.birthDate;
            ds.nationality = payload.nationality;
            exitEditMode();
            toast(result.message || '회원정보를 수정했습니다.');
        } else {
            alert(result.message || '저장에 실패했습니다.');
        }
    });

    // ===================== 2. 소셜 연동 =====================
    // [social-link 신규] 구글/네이버만 지원(application.yml 에 설정된 provider - 라인은 주석
    // 처리돼 있어 제외, OAuthAttributes.of 도 "line" 을 지원 안 함).
    var SOCIAL_PROVIDERS = ['google', 'naver'];
    var PROVIDER_LABEL = { google: '구글', naver: '네이버' };
    var PROVIDER_ICON = { google: '🅶', naver: 'N' };

    var socialIcon = document.getElementById('social-icon');
    var socialTitle = document.getElementById('social-title');
    var socialSub = document.getElementById('social-sub');
    var socialActions = document.getElementById('social-link-actions');

    // [social-link] 연동은 영구적이라(해제 기능 없음 - 코디네이터 지시), 링크로 이동하기 전에
    // 반드시 확인을 받는다. 이 앱에 이미 confirm() 을 쓰는 파괴적 동작(차단, 여행 삭제 등)이
    // 여럿 있어 그 관례를 그대로 따른다 - 새 확인 모달을 만들지 않는다.
    function confirmAndGoToLink(provider, href) {
        var label = PROVIDER_LABEL[provider] || provider;
        if (confirm(label + ' 계정 연동이 완료되면 해제할 수 없습니다. 연동하시겠습니까?')) {
            window.location.href = href;
        }
    }

    function renderSocialTab() {
        socialActions.innerHTML = '';
        if (isSocial) {
            var provider = ds.provider || '';
            var label = PROVIDER_LABEL[provider] || provider;
            socialIcon.textContent = PROVIDER_ICON[provider] || '🔗';
            socialTitle.textContent = label + ' 계정으로 연동됨';
            socialSub.textContent = label + ' 로그인으로 가입한 계정이에요. 한 번 연동하면 해제할 수 없어요.';
            // 이미 연동된 계정은 위 카드만 보여주고 끝 - 해제 버튼 없음(영구 연동).
        } else {
            socialIcon.textContent = '🔒';
            socialTitle.textContent = '일반 로그인 계정';
            socialSub.textContent = '이메일과 비밀번호로 로그인해요.';

            SOCIAL_PROVIDERS.forEach(function (provider) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'btn btn-forest';
                btn.textContent = (PROVIDER_LABEL[provider] || provider) + '로 연동하기';
                btn.addEventListener('click', function () {
                    confirmAndGoToLink(provider, '/mypage/account/social/link/' + provider);
                });
                socialActions.appendChild(btn);
            });
        }
    }
    renderSocialTab();

    // [social-link 신규] 연동 콜백이 끝나고 돌아오면 LoginSuccessHandler가
    // /mypage/account?tab=social&linked=1 로, 실패는 /login?error=... 로 보낸다(재로그인 필요 -
    // CustomOAuth2UserService 주석 참고). 여기서는 성공 케이스(linked=1)와, "이미 연동됨" 방어용
    // 서버 리다이렉트(linkError=already_linked)만 토스트로 보여준다.
    (function handleLinkQueryParams() {
        var params = new URLSearchParams(location.search);
        if (params.get('linked') === '1') {
            showTab('social');
            toast('소셜 계정을 연동했습니다.');
        } else if (params.get('linkError') === 'already_linked') {
            showTab('social');
            toast('이미 소셜 계정이 연동되어 있어요.');
        }
        if (params.has('linked') || params.has('linkError')) {
            history.replaceState(null, '', location.pathname);
        }
    })();

    // ===================== 3. 알림 설정 =====================
    var nPush = document.getElementById('n-push');
    var nEmail = document.getElementById('n-email');
    var nFocus = document.getElementById('n-focus');
    var nFollower = document.getElementById('n-follower');
    var nComment = document.getElementById('n-comment');
    var nPartyApp = document.getElementById('n-party-app');
    var nPartyApproved = document.getElementById('n-party-approved');
    var nPartyRejected = document.getElementById('n-party-rejected');
    var nPartyKicked = document.getElementById('n-party-kicked');
    var nTripReminder = document.getElementById('n-trip-reminder');

    function boolAttr(v) { return v === 'true'; }
    nPush.checked = boolAttr(ds.push);
    nEmail.checked = boolAttr(ds.emailNoti);
    nFocus.checked = boolAttr(ds.focus);
    nFollower.checked = boolAttr(ds.nFollower);
    nComment.checked = boolAttr(ds.nComment);
    nPartyApp.checked = boolAttr(ds.nPartyApp);
    nPartyApproved.checked = boolAttr(ds.nPartyApproved);
    nPartyRejected.checked = boolAttr(ds.nPartyRejected);
    nPartyKicked.checked = boolAttr(ds.nPartyKicked);
    nTripReminder.checked = boolAttr(ds.nTripReminder);

    document.getElementById('btn-save-notify').addEventListener('click', async function () {
        var payload = {
            pushEnabled: nPush.checked,
            emailEnabled: nEmail.checked,
            focusModeEnabled: nFocus.checked,
            notifyNewFollower: nFollower.checked,
            notifyNewComment: nComment.checked,
            notifyPartyApplication: nPartyApp.checked,
            notifyPartyApproved: nPartyApproved.checked,
            notifyPartyRejected: nPartyRejected.checked,
            notifyPartyKicked: nPartyKicked.checked,
            notifyTripReminder: nTripReminder.checked
        };
        var result = await window.api.put('/api/mypage/account/notifications', payload);
        if (result.success) toast(result.message || '알림 설정을 저장했습니다.');
        else alert(result.message || '저장에 실패했습니다.');
    });

    // ===================== 4. 계정 공개범위 =====================
    var privacyPublic = document.getElementById('privacy-public');
    var privacyPrivate = document.getElementById('privacy-private');
    if (boolAttr(ds.private)) privacyPrivate.checked = true; else privacyPublic.checked = true;

    document.getElementById('btn-save-privacy').addEventListener('click', async function () {
        var isPrivate = privacyPrivate.checked;
        var result = await window.api.put('/api/mypage/account/privacy', { isPrivate: isPrivate });
        if (result.success) toast(result.message || '저장했습니다.');
        else alert(result.message || '저장에 실패했습니다.');
    });

    // ===================== 5. 차단 계정 관리 =====================
    // 기존 /api/block/{id} DELETE 를 그대로 쓴다(public-profile.html/room.html과 동일한 호출 패턴).
    document.querySelectorAll('.btn-unblock').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var targetId = btn.dataset.targetId;
            if (!confirm('차단을 해제하시겠습니까?')) return;
            var result = await window.api.del('/api/block/' + targetId);
            if (result.success) {
                var row = btn.closest('.block-row');
                if (row) row.remove();
                var list = document.getElementById('block-list');
                if (list && !list.querySelector('.block-row')) {
                    var empty = document.getElementById('block-empty');
                    if (empty) empty.style.display = 'block';
                    else {
                        var p = document.createElement('p');
                        p.id = 'block-empty';
                        p.style.cssText = 'color:var(--ink-soft); font-size:13px;';
                        p.textContent = '차단한 계정이 없습니다.';
                        list.after(p);
                    }
                }
                toast('차단을 해제했습니다.');
            } else {
                alert(result.message || '해제에 실패했습니다.');
            }
        });
    });
})();

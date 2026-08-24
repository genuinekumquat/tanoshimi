/** 패키지 목록의 지역/정렬 필터 - 바꾸면 해당 조건으로 페이지를 다시 불러온다. */
(function () {
    const regionSelect = document.getElementById('f-region');
    const sortSelect = document.getElementById('f-sort');
    if (!regionSelect || !sortSelect) return;

    function reload() {
        const params = new URLSearchParams();
        if (regionSelect.value) params.set('region', regionSelect.value);
        if (sortSelect.value && sortSelect.value !== 'recommend') params.set('sort', sortSelect.value);
        if (PARTY_ID_FOR_FILTER) params.set('partyId', PARTY_ID_FOR_FILTER);
        const qs = params.toString();
        window.location.href = '/packages' + (qs ? '?' + qs : '');
    }

    regionSelect.addEventListener('change', reload);
    sortSelect.addEventListener('change', reload);
})();

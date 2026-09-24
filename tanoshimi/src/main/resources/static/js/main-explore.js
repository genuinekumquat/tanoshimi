/**
 * 메인 페이지 카드 그리드 렌더링.
 *
 * <p>이 파일은 예전엔 인터랙티브 지도(일본/한국 SVG, 명소 오버레이)까지 담당했지만,
 * 지도 영역이 파티 찾기 흐름과 직접 연결되지 않아 index.html 개편(feature/main-page-revamp)에서
 * 통째로 제거했다. 지금 하는 일은 두 가지뿐이다.
 *
 * <ol>
 *   <li>태그 필터 pill 렌더링 + 클릭 시 "모집 마감 임박 파티" 그리드 재필터</li>
 *   <li>서버가 내려준 두 목록을 카드로 렌더링
 *     <ul>
 *       <li>{@code HOT_PARTIES}  → #hot-party-grid : 7일 안에 출발하는 모집중 파티(출발일 빠른 순) 상위 6개를 D-day/잔여석이 보이는 리스트로. 클릭 시 /party-board/{id}</li>
 *       <li>{@code POPULAR_SNAPS} → #snap-grid      : 좋아요순 커뮤니티 사진 글. 클릭 시 /board/{id}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>{@code HOT_PARTIES}/{@code POPULAR_SNAPS} 는 index.html 하단 th:inline 스크립트가
 * 이 파일보다 먼저(파싱 중) 전역에 정의한다 - defer 로 로드되는 이 파일은 그 이후에 실행된다.
 */
(function() {

  function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }
  window.escapeHtml = escapeHtml;

  /* ================= 태그 필터 ================= *
   * key 값은 PartyEntity.styleTag(= party/create.html select 옵션)와 정확히 일치한다.
   * 지금은 이미 내려받은 HOT_PARTIES 안에서만 클라이언트 필터링한다 - 전체 파티보드 대상
   * 서버 검색이 필요하면 /party-board?q=... 로 넘기는 방식으로 확장할 수 있다(별도 작업).
   */
  const TAGS = [
    { key: '전체',      label: '전체' },
    { key: '먹거리',    label: '🍕 먹거리' },
    { key: '축제',      label: '🎉 축제' },
    { key: '문화체험',  label: '👘 문화체험' },
    { key: '액티비티',  label: '🏄‍♂️ 액티비티' },
    { key: '힐링',      label: '☕ 힐링' }
  ];

  let activeTag = '전체';

  const tagRow = document.getElementById('tag-row');
  if (tagRow) {
    tagRow.innerHTML = TAGS.map(t => `
      <div class="tag-pill ${t.key === '전체' ? 'on' : ''}" data-tag="${t.key}">
        <span>${t.label}</span>
      </div>`).join('');

    tagRow.querySelectorAll('.tag-pill').forEach(el => {
      el.addEventListener('click', () => {
        activeTag = el.dataset.tag;
        tagRow.querySelectorAll('.tag-pill').forEach(p => p.classList.toggle('on', p.dataset.tag === activeTag));
        renderHotPartyGrid();
      });
    });
  }

  /* ================= 1. 모집 마감 임박 파티 리스트 (홈 왼쪽 칸) ================= */
  const PH_CYCLE = ['ph1', 'ph2', 'ph3', 'ph4'];
  const HOT_PARTY_LIMIT = 6;

  /** PartyCardView.departureDate("yyyy.MM.dd") 기준 D-day 라벨. 서버가 지난 파티는 이미 뺀다. */
  function dDayLabel(departureDate) {
    const [y, m, d] = String(departureDate || '').split('.').map(Number);
    if (!y || !m || !d) return '';
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const days = Math.round((new Date(y, m - 1, d) - today) / 86400000);
    return days <= 0 ? 'D-DAY' : 'D-' + days;
  }

  function renderHotPartyGrid() {
    const grid = document.getElementById('hot-party-grid');
    const empty = document.getElementById('hot-party-empty');
    if (!grid) return;

    const source = (typeof HOT_PARTIES !== 'undefined' && Array.isArray(HOT_PARTIES)) ? HOT_PARTIES : [];
    const list = source
      .filter(p => activeTag === '전체' || (p.styleTag && p.styleTag === activeTag))
      .slice(0, HOT_PARTY_LIMIT);

    if (!list.length) {
      grid.innerHTML = '';
      if (empty) {
        // 서버는 7일 안에 출발하는 파티만 내려준다 - 아예 없을 때와 태그 필터로 걸러졌을 때 문구를 나눈다.
        empty.innerHTML = source.length
          ? '이 카테고리에는 일주일 안에 출발하는 파티가 없어요.'
          : '일주일 안에 출발하는 파티가 없어요.<br><a href="/party-board" style="color:var(--forest); font-weight:800;">파티 게시판에서 찾아보기 →</a>';
        empty.style.display = 'block';
      }
      return;
    }
    if (empty) empty.style.display = 'none';

    grid.innerHTML = list.map((p, i) => {
      const isUpload = p.thumbnailUrl && !p.thumbnailUrl.startsWith('ph');
      const phClass = isUpload ? PH_CYCLE[i % PH_CYCLE.length] : (p.thumbnailUrl || PH_CYCLE[i % PH_CYCLE.length]);
      let thumbSrc = isUpload ? p.thumbnailUrl : '';
      if (isUpload && !thumbSrc.startsWith('http') && !thumbSrc.startsWith('/')) {
        thumbSrc = '/uploads/' + thumbSrc;
      }
      const thumbInner = isUpload
        ? `<img src="${thumbSrc}" alt="" onerror="this.style.display='none';">`
        : `<div class="ph ${phClass}"></div>`;

      const remaining = Math.max(p.capacity - (p.joinedCount || 0), 0);
      return `
      <a class="party-row" href="/party-board/${p.id}">
        <div class="thumb">${thumbInner}</div>
        <div class="info">
          <p class="t">${escapeHtml(p.title)}</p>
          <div class="m">📍 ${escapeHtml(p.region)} · ${escapeHtml(p.departureDate)} 출발 · 멤버 ${p.joinedCount || 0}/${p.capacity}</div>
        </div>
        <div class="badges">
          <span class="dday">${dDayLabel(p.departureDate)}</span>
          <span class="seat ${remaining <= 1 ? 'last' : ''}">잔여 ${remaining}석</span>
        </div>
      </a>`;
    }).join('');
  }

  /* ================= 2. 인기 스냅 그리드 (커뮤니티 사진 글) ================= */
  function renderSnapGrid() {
    const grid = document.getElementById('snap-grid');
    const empty = document.getElementById('snap-empty');
    if (!grid) return;

    const list = (typeof POPULAR_SNAPS !== 'undefined' && Array.isArray(POPULAR_SNAPS)) ? POPULAR_SNAPS : [];

    if (!list.length) {
      grid.innerHTML = '';
      if (empty) empty.style.display = 'block';
      return;
    }
    if (empty) empty.style.display = 'none';

    grid.innerHTML = list.map((p, i) => {
      // board/list.html 과 동일하게: thumbnailUrl 이 실제 경로가 아니면(과거 'ph1' 등 시드값)
      // 업로드 이미지가 아니라 팔레트 플레이스홀더로 취급한다.
      const isUpload = p.thumbnailUrl && !p.thumbnailUrl.startsWith('ph');
      let thumbInner;
      if (isUpload) {
        let thumbSrc = p.thumbnailUrl;
        if (!thumbSrc.startsWith('http') && !thumbSrc.startsWith('/')) {
          thumbSrc = '/uploads/' + thumbSrc;
        }
        thumbInner = `<img src="${thumbSrc}" alt="" onerror="this.style.display='none';">`;
      } else {
        const phClass = (p.thumbnailUrl && p.thumbnailUrl.startsWith('ph')) ? p.thumbnailUrl : PH_CYCLE[i % PH_CYCLE.length];
        thumbInner = `<div class="ph ${phClass}"></div>`;
      }
      const region = p.region ? `<span class="rg">📍 ${escapeHtml(p.region)} · </span>` : '';
      return `
      <a class="snap-card" href="/board/${p.id}">
        ${thumbInner}
        <div class="snap-overlay">
          <p class="t">${escapeHtml(p.title)}</p>
          <div class="m">${region}❤️ ${p.likeCount || 0}</div>
        </div>
      </a>`;
    }).join('');
  }

  renderHotPartyGrid();
  renderSnapGrid();

})();

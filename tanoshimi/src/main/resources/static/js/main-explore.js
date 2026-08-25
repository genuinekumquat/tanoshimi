(function() {
  function getClosestRegion(clickX, clickY, mapType, regionsMap) {
    let closestId = null;
    let closestName = null;
    let minDist = Infinity;
    
    let targetRegions = Object.assign({}, regionsMap);
    
    if (mapType === 'jp' && typeof JAPAN_REGIONS !== 'undefined') {
        targetRegions['hok_1'] = { name: '홋카이도', cx: 80, cy: 10, realId: 'hokkaido' };
    }
    
    for (const uid in targetRegions) {
      const r = targetRegions[uid];
      const dx = clickX - r.cx;
      const dy = clickY - r.cy;
      const dist = (dx*dx) + (dy*dy);
      
      if (dist < minDist) {
        minDist = dist;
        closestId = r.realId ? r.realId : uid;
        closestName = r.name;
      }
    }
    
    if (minDist > 200) { 
        return { id: null, name: null };
    }
    
    return { id: closestId, name: closestName };
  }

  function handleMapInteraction(e, mapType, frameId, isClick) {
    const frame = document.getElementById(frameId);
    if (!frame) return;
    
    const rect = frame.getBoundingClientRect();
    const xRatio = (e.clientX - rect.left) / rect.width;
    const yRatio = (e.clientY - rect.top) / rect.height;
    
    const viewBoxW = 100;
    const viewBoxH = mapType === 'kr' ? 140 : 100;
    
    const clickX = xRatio * viewBoxW;
    const clickY = yRatio * viewBoxH;
    
    const regionsMap = mapType === 'kr' ? (typeof KOREA_REGIONS !== 'undefined' ? KOREA_REGIONS : {}) 
                                        : (typeof JAPAN_REGIONS !== 'undefined' ? JAPAN_REGIONS : {});
                                        
    const closest = getClosestRegion(clickX, clickY, mapType, regionsMap);
    
    const tooltip = document.getElementById('map-tooltip');
    
    if (isClick) {
        if (closest.id) openSpotOverlay(closest.id, closest.name);
    } else {
        if (tooltip) {
            if (closest.name && !document.getElementById('spot-overlay').classList.contains('on')) {
                tooltip.style.opacity = '1';
                tooltip.textContent = closest.name;
                
                const innerRect = document.getElementById('map-explore-inner').getBoundingClientRect();
                const mouseX = e.clientX - innerRect.left;
                const mouseY = e.clientY - innerRect.top;
                
                tooltip.style.left = mouseX + 'px';
                tooltip.style.top = (mouseY - 10) + 'px';
            } else {
                tooltip.style.opacity = '0';
            }
        }
    }
  }

  function setupMap() {
    const svgKr = document.getElementById('explore-kr');
    if(svgKr) svgKr.innerHTML = '';
    const svgJp = document.getElementById('explore-jp');
    if(svgJp) svgJp.innerHTML = '';

    const frameKr = document.getElementById('explore-frame-kr');
    if (frameKr) {
      frameKr.addEventListener('click', (e) => handleMapInteraction(e, 'kr', 'explore-frame-kr', true));
      frameKr.addEventListener('mousemove', (e) => handleMapInteraction(e, 'kr', 'explore-frame-kr', false));
      frameKr.addEventListener('mouseleave', () => {
          const t = document.getElementById('map-tooltip');
          if (t) t.style.opacity = '0';
      });
    }

    const frameJp = document.getElementById('explore-frame-jp');
    if (frameJp) {
      frameJp.addEventListener('click', (e) => handleMapInteraction(e, 'jp', 'explore-frame-jp', true));
      frameJp.addEventListener('mousemove', (e) => handleMapInteraction(e, 'jp', 'explore-frame-jp', false));
      frameJp.addEventListener('mouseleave', () => {
          const t = document.getElementById('map-tooltip');
          if (t) t.style.opacity = '0';
      });
    }
  }

  function openSpotOverlay(regionId, regionName) {
    const overlay = document.getElementById('spot-overlay');
    const inner = document.getElementById('map-explore-inner');
    const orbit = document.getElementById('spot-orbit');
    const title = document.getElementById('spot-title');
    const tooltip = document.getElementById('map-tooltip');
    
    if(!overlay) return;
    if(tooltip) tooltip.style.opacity = '0'; 

    title.textContent = regionName;
    const places = (typeof FAMOUS_PLACES !== 'undefined') ? FAMOUS_PLACES[regionId] : null;

    if (!places || places.length === 0) {
      orbit.innerHTML = `<p class="spot-empty" style="text-align:center;color:#666; font-size:16px; font-weight:700;">추천 명소를 발굴하고 있어요!</p>`;
    } else {
      const n = places.length;
      const radiusX = Math.min(orbit.clientWidth || 380, 460) / 2 - 60;
      const radiusY = Math.min(orbit.clientHeight || 280, 340) / 2 - 55;

      orbit.innerHTML = places.map((p, i) => {
        const angle = (Math.PI * 2 / n) * i - Math.PI / 2;
        const tx = Math.cos(angle) * radiusX;
        const ty = Math.sin(angle) * radiusY;
        
        const fallbackImg = `https://picsum.photos/seed/${encodeURIComponent(p.name)}/400/300`;
        const imgUrl = p.img ? p.img : fallbackImg;
        
        return `
          <div class="spot-card" data-name="${escapeHtml(p.name)}" data-desc="${escapeHtml(p.desc)}" data-img="${imgUrl}" style="transition-delay:${i * 60}ms; --tx:${tx}px; --ty:${ty}px;">
            <div class="ph" style="background-image:url('${imgUrl}')"></div>
            <div class="info">
              <p class="t">${p.name}</p>
              <p class="d">${p.desc}</p>
            </div>
          </div>`;
      }).join('');
    }

    inner.classList.add('dimmed');
    overlay.classList.add('on');

    requestAnimationFrame(() => {
      document.querySelectorAll('.spot-card').forEach(card => {
        card.style.transform = `translate(var(--tx), var(--ty)) scale(1)`;
        card.addEventListener('click', function(e) {
            e.stopPropagation();
            if (typeof window.openSpotDetailModal === 'function') {
                window.openSpotDetailModal(this.dataset.name, this.dataset.desc, this.dataset.img);
            }
        });
      });
    });
  }

  function closeSpotOverlay() {
    const overlay = document.getElementById('spot-overlay');
    if (!overlay) return;
    overlay.classList.remove('on');
    document.getElementById('map-explore-inner')?.classList.remove('dimmed');
    document.querySelectorAll('.spot-card').forEach(card => { 
        if(card.style) card.style.transform = 'translate(0,0) scale(.4)'; 
    });
  }

  document.getElementById('spot-close')?.addEventListener('click', closeSpotOverlay);
  document.getElementById('spot-overlay')?.addEventListener('click', e => {
    if (e.target.id === 'spot-overlay') closeSpotOverlay();
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeSpotOverlay(); });

  function setExploreMapMode(mode) {
    document.getElementById('explore-toggle')?.classList.toggle('kr-active', mode === 'kr');
    document.getElementById('explore-btn-jp')?.classList.toggle('on', mode === 'jp');
    document.getElementById('explore-btn-kr')?.classList.toggle('on', mode === 'kr');
    if(document.getElementById('explore-frame-jp')) document.getElementById('explore-frame-jp').style.display = mode === 'jp' ? '' : 'none';
    if(document.getElementById('explore-frame-kr')) document.getElementById('explore-frame-kr').style.display = mode === 'kr' ? '' : 'none';
    closeSpotOverlay();
    const tooltip = document.getElementById('map-tooltip');
    if(tooltip) tooltip.style.opacity = '0';
  }
  
  document.getElementById('explore-btn-jp')?.addEventListener('click', () => setExploreMapMode('jp'));
  document.getElementById('explore-btn-kr')?.addEventListener('click', () => setExploreMapMode('kr'));

  setupMap(); 

  const TAGS = [
    { key: '전체', label: '전체' },
    { key: '먹거리',  label: '🍕 먹거리' },
    { key: '축제', label: '🎉 축제' },
    { key: '문화체험', label: '👘 문화체험' },
    { key: '액티비티', label: '🏄‍♂️ 액티비티' },
    { key: '힐링', label: '☕ 힐링' }
  ];

  let activeTag = '전체';

  const tagRow = document.getElementById('tag-row');
  if (tagRow) {
      tagRow.innerHTML = TAGS.map(t => `
        <div class="tag-pill ${t.key === '전체' ? 'on' : ''}" data-tag="${t.key}">
          <span>${t.label}</span>
        </div>`).join('');

      document.querySelectorAll('.tag-pill').forEach(el => {
        el.addEventListener('click', () => {
          activeTag = el.dataset.tag;
          document.querySelectorAll('.tag-pill').forEach(p => p.classList.toggle('on', p.dataset.tag === activeTag));
          renderSnapGrid();
        });
      });
  }

  function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  const PH_CYCLE = ['ph1', 'ph2', 'ph3', 'ph4'];

  function renderSnapGrid() {
    if (typeof SERVER_PARTIES === 'undefined' || !SERVER_PARTIES || !Array.isArray(SERVER_PARTIES)) {
      const grid = document.getElementById('snap-grid');
      const empty = document.getElementById('snap-empty');
      if (grid) grid.innerHTML = '';
      if (empty) empty.style.display = 'block';
      return;
    }
    
    let list = SERVER_PARTIES.filter(p => activeTag === '전체' || (p.styleTag && p.styleTag === activeTag));
    list = list.slice(0, 20);

    const grid = document.getElementById('snap-grid');
    const empty = document.getElementById('snap-empty');
    if (!grid) return;

    if (!list.length) {
      grid.innerHTML = '';
      if (empty) empty.style.display = 'block';
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
        ? `<img src="${thumbSrc}" onerror="this.style.display='none';">`
        : `<div class="ph ${phClass}"></div>`;
        
      return `
      <a class="snap-card" href="/party-board/${p.id}">
        ${thumbInner}
        <div class="snap-overlay">
          <p class="t">${escapeHtml(p.title)}</p>
          <div class="m">📍 ${escapeHtml(p.region)} <br> 멤버 ${p.joinedCount || 0}/${p.capacity}</div>
        </div>
      </a>`;
    }).join('');
  }

  window.escapeHtml = escapeHtml;

  setTimeout(() => renderSnapGrid(), 0);

})();

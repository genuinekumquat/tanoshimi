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

    // [TNSM-53] 이 지역 SNAP(사진 후기) 목록으로 이동하는 링크. board 목록의 지역 필터를
    // 그대로 재사용한다(정확히 일치하는 region 값 기준 - 상위 지역/하위 지역 매칭까지는
    // 하지 않는다. 마이페이지 지도의 RegionCatalog 기반 매칭과는 별개 범위).
    const snapLink = document.getElementById('spot-snap-link');
    if (snapLink) snapLink.href = '/board?region=' + encodeURIComponent(regionName);

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

  function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  function renderSnapGrid() {
    const grid = document.getElementById('snap-grid');
    const empty = document.getElementById('snap-empty');
    if (!grid) return;

    if (typeof SERVER_SNAPS === 'undefined' || !SERVER_SNAPS || !Array.isArray(SERVER_SNAPS) || !SERVER_SNAPS.length) {
      grid.innerHTML = '';
      if (empty) empty.style.display = 'block';
      return;
    }
    if (empty) empty.style.display = 'none';

    grid.innerHTML = SERVER_SNAPS.map((p) => {
      return `
      <a class="snap-card" href="/board/${p.id}">
        <img src="${p.thumbnailUrl}" onerror="this.style.display='none';">
        <div class="snap-overlay">
          <p class="t">${escapeHtml(p.title)}</p>
          <div class="m">📍 ${escapeHtml(p.region || '지역미정')} · ❤️ ${p.likeCount || 0}</div>
        </div>
      </a>`;
    }).join('');
  }

  window.escapeHtml = escapeHtml;

  setTimeout(() => renderSnapGrid(), 0);

})();

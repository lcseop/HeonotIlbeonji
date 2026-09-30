(() => {
  const host = document.querySelector('#areaMap');
  const chips = document.querySelector('#areaDistricts');
  const status = document.querySelector('#areaMapStatus');
  if (!host || !chips || !status) return;

  const ns = 'http://www.w3.org/2000/svg';
  const bounds = { west: 126.54, east: 127.01, south: 37.50, north: 38.01 };
  let areas = [];
  let renderers = [];
  let selected = '';

  function focus(name) {
    selected = selected === name ? '' : name;
    chips.querySelectorAll('button').forEach(button => {
      const active = button.dataset.area === selected;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    renderers.forEach(({ name: areaName, shape, label }) => {
      const faded = !!selected && selected !== areaName;
      shape?.setOptions?.({ fillOpacity: faded ? 0.09 : 0.29, strokeOpacity: faded ? 0.28 : 0.95, strokeWeight: selected === areaName ? 4 : 2 });
      if (shape?.style) { shape.style.opacity = faded ? '.22' : '1'; shape.style.strokeWidth = selected === areaName ? '4' : '2'; }
      if (label?.style) label.style.opacity = faded ? '.4' : '1';
    });
  }

  function makeSvg(loaded) {
    host.replaceChildren();
    renderers = [];
    const svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 700 480');
    svg.setAttribute('aria-hidden', 'true');
    svg.classList.add('area-map-fallback');
    const x = lng => 40 + (lng - bounds.west) / (bounds.east - bounds.west) * 620;
    const y = lat => 20 + (bounds.north - lat) / (bounds.north - bounds.south) * 440;
    for (let i = 1; i < 5; i++) {
      const line = document.createElementNS(ns, 'line');
      line.setAttribute('x1', 40); line.setAttribute('x2', 660);
      line.setAttribute('y1', 20 + i * 88); line.setAttribute('y2', 20 + i * 88);
      line.setAttribute('class', 'area-map-grid'); svg.append(line);
    }
    loaded.areas.forEach(area => {
      const path = document.createElementNS(ns, 'polygon');
      path.setAttribute('points', area.points.map(([lat, lng]) => `${x(lng)},${y(lat)}`).join(' '));
      path.setAttribute('fill', area.color); path.setAttribute('stroke', area.color);
      path.setAttribute('class', 'area-map-shape');
      path.addEventListener('click', () => focus(area.name));
      svg.append(path);
      const center = area.points.reduce((sum, [lat, lng]) => [sum[0] + lat, sum[1] + lng], [0, 0]).map(v => v / area.points.length);
      const text = document.createElementNS(ns, 'text');
      text.setAttribute('x', x(center[1])); text.setAttribute('y', y(center[0]));
      text.setAttribute('class', 'area-map-label'); text.textContent = area.name;
      svg.append(text); renderers.push({ name: area.name, shape: path, label: text });
    });
    host.append(svg);
    status.textContent = '지역 범위 미리보기';
  }

  function renderKakao(loaded) {
    host.replaceChildren(); renderers = [];
    const map = new kakao.maps.Map(host, { center: new kakao.maps.LatLng(...loaded.center), level: loaded.level });
    map.setMinLevel(7); map.setMaxLevel(12);
    loaded.areas.forEach(area => {
      const points = area.points.map(([lat, lng]) => new kakao.maps.LatLng(lat, lng));
      const shape = new kakao.maps.Polygon({ map, path: points, strokeWeight: 2, strokeColor: area.color, strokeOpacity: .95, fillColor: area.color, fillOpacity: .29 });
      const center = area.points.reduce((sum, [lat, lng]) => [sum[0] + lat, sum[1] + lng], [0, 0]).map(v => v / area.points.length);
      const badge = document.createElement('span'); badge.className = 'area-map-kakao-label'; badge.textContent = area.name;
      const label = new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(...center), content: badge, yAnchor: .5 });
      kakao.maps.event.addListener(shape, 'click', () => focus(area.name));
      badge.addEventListener('click', () => focus(area.name));
      renderers.push({ name: area.name, shape, label: badge });
    });
    status.textContent = '카카오맵';
    if (selected) { const previous = selected; selected = ''; focus(previous); }
  }

  function loadSdk(key, loaded) {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.onload = () => window.kakao?.maps?.load(() => renderKakao(loaded));
    script.onerror = () => { status.textContent = '지역 범위 미리보기'; };
    document.head.append(script);
  }

  async function start() {
    try {
      const response = await fetch('/data/service-areas.json');
      if (!response.ok) throw new Error('area-data');
      const loaded = await response.json();
      areas = loaded.areas;
      if (!Array.isArray(areas) || !areas.length) throw new Error('area-data');
      makeSvg(loaded);
      areas.forEach(area => {
        const button = document.createElement('button'); button.type = 'button';
        button.dataset.area = area.name; button.textContent = area.name;
        button.style.setProperty('--area-color', area.color);
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => focus(area.name));
        chips.append(button);
      });
      const config = await fetch('/api/map-config', { cache: 'no-store' }).then(r => r.ok ? r.json() : { key: '' }).catch(() => ({ key: '' }));
      if (config.key) loadSdk(config.key, loaded);
    } catch { status.textContent = '지역 정보를 불러오지 못했습니다'; }
  }
  start();
})();

(() => {
  const $ = id => document.getElementById(id);
  const ns = 'http://www.w3.org/2000/svg';
  const storageKey = 'heonot-service-areas-draft-v1';
  const bounds = { west: 126.51, east: 127.03, south: 37.35, north: 38.00 };
  const mapHost = $('editorMap');
  let published, data, selected = 0, selectedPoint = -1, addMode = false;
  let kakaoMap = null, overlays = [], markers = [], svg;

  const x = lng => 40 + (lng - bounds.west) / (bounds.east - bounds.west) * 620;
  const y = lat => 20 + (bounds.north - lat) / (bounds.north - bounds.south) * 440;
  const lngFromX = px => bounds.west + (px - 40) / 620 * (bounds.east - bounds.west);
  const latFromY = py => bounds.north - (py - 20) / 440 * (bounds.north - bounds.south);
  const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
  const copy = value => JSON.parse(JSON.stringify(value));
  const notice = text => { $('editorNotice').textContent = text; };
  const hint = text => { $('editorHint').textContent = text; };

  function valid(value) {
    return value && Array.isArray(value.areas) && value.areas.length > 0 && value.areas.length <= 30 && value.areas.every(area =>
      typeof area.name === 'string' && area.name.length > 0 && area.name.length <= 20 && /^#[0-9a-f]{6}$/i.test(area.color) &&
      Array.isArray(area.points) && area.points.length >= 3 && area.points.length <= 80 && area.points.every(point =>
        Array.isArray(point) && point.length === 2 && point.every(Number.isFinite) && point[0] >= 33 && point[0] <= 39 && point[1] >= 124 && point[1] <= 132));
  }

  function syncControls() {
    const area = data.areas[selected];
    $('areaSelect').replaceChildren();
    data.areas.forEach((item, index) => {
      const option = document.createElement('option'); option.value = index; option.textContent = item.name;
      $('areaSelect').append(option);
    });
    $('areaSelect').value = selected;
    $('areaName').value = area.name;
    $('areaColor').value = area.color;
    $('removePoint').disabled = selectedPoint < 0 || area.points.length <= 3;
    $('deleteArea').disabled = data.areas.length <= 1;
    $('addPoint').textContent = addMode ? '추가 취소' : '꼭짓점 추가';
  }

  function projectPointer(event) {
    const rect = svg.getBoundingClientRect();
    const px = (event.clientX - rect.left) / rect.width * 700;
    const py = (event.clientY - rect.top) / rect.height * 480;
    return [Number(clamp(latFromY(py), bounds.south, bounds.north).toFixed(5)), Number(clamp(lngFromX(px), bounds.west, bounds.east).toFixed(5))];
  }

  function renderSvg() {
    svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 700 480');
    svg.setAttribute('aria-label', '수거 지역 경계 편집 지도');
    const projected = document.createElementNS(ns, 'rect');
    projected.setAttribute('width', 700); projected.setAttribute('height', 480); projected.setAttribute('fill', 'transparent');
    svg.append(projected);
    for (let i = 1; i < 5; i++) {
      const line = document.createElementNS(ns, 'line');
      line.setAttribute('x1', 40); line.setAttribute('x2', 660); line.setAttribute('y1', 20 + i * 88); line.setAttribute('y2', 20 + i * 88);
      line.setAttribute('stroke', '#fff'); line.setAttribute('opacity', '.75'); svg.append(line);
    }
    data.areas.forEach((area, areaIndex) => {
      const polygon = document.createElementNS(ns, 'polygon');
      polygon.setAttribute('points', area.points.map(([lat, lng]) => `${x(lng)},${y(lat)}`).join(' '));
      polygon.setAttribute('fill', area.color); polygon.setAttribute('stroke', area.color);
      if (areaIndex !== selected) polygon.classList.add('dim');
      polygon.addEventListener('click', event => { event.stopPropagation(); if (addMode) addPoint(projectPointer(event)); else selectArea(areaIndex); });
      svg.append(polygon);
      const center = area.points.reduce((sum, [lat, lng]) => [sum[0] + lat, sum[1] + lng], [0, 0]).map(v => v / area.points.length);
      const label = document.createElementNS(ns, 'text'); label.textContent = area.name;
      label.setAttribute('x', x(center[1])); label.setAttribute('y', y(center[0])); svg.append(label);
    });
    data.areas[selected].points.forEach(([lat, lng], pointIndex) => {
      const circle = document.createElementNS(ns, 'circle');
      circle.setAttribute('cx', x(lng)); circle.setAttribute('cy', y(lat)); circle.setAttribute('r', 7);
      if (selectedPoint === pointIndex) circle.classList.add('selected');
      circle.addEventListener('pointerdown', event => {
        event.preventDefault(); event.stopPropagation(); selectedPoint = pointIndex; circle.setPointerCapture(event.pointerId);
        syncControls();
        svg.querySelectorAll('circle').forEach((item, index) => item.classList.toggle('selected', index === pointIndex));
      });
      circle.addEventListener('pointermove', event => {
        if (!circle.hasPointerCapture(event.pointerId)) return;
        const point = projectPointer(event);
        data.areas[selected].points[pointIndex] = point;
        circle.setAttribute('cx', x(point[1])); circle.setAttribute('cy', y(point[0]));
        const polygon = svg.querySelectorAll('polygon')[selected];
        polygon.setAttribute('points', data.areas[selected].points.map(([a, b]) => `${x(b)},${y(a)}`).join(' '));
      });
      circle.addEventListener('pointerup', event => {
        if (circle.hasPointerCapture(event.pointerId)) circle.releasePointerCapture(event.pointerId);
        hint('경계를 수정했습니다. 아래에서 임시 저장하거나 설정 파일을 다운로드하세요.');
      });
      svg.append(circle);
    });
    svg.addEventListener('click', event => { if (addMode && event.target === projected) addPoint(projectPointer(event)); });
    mapHost.replaceChildren(svg);
    $('editorMapStatus').textContent = '도형 미리보기 · 카카오맵 키 대기 중';
  }

  function clearKakao() {
    overlays.forEach(item => item.setMap(null)); markers.forEach(item => item.setMap(null));
    overlays = []; markers = [];
  }

  function renderKakao() {
    clearKakao();
    data.areas.forEach((area, index) => {
      const path = area.points.map(([lat, lng]) => new kakao.maps.LatLng(lat, lng));
      const polygon = new kakao.maps.Polygon({ map: kakaoMap, path, strokeWeight: index === selected ? 4 : 2,
        strokeColor: area.color, strokeOpacity: index === selected ? 1 : .55,
        fillColor: area.color, fillOpacity: index === selected ? .35 : .13 });
      kakao.maps.event.addListener(polygon, 'click', event => {
        if (addMode) addPoint([event.latLng.getLat(), event.latLng.getLng()]);
        else selectArea(index);
      });
      overlays.push(polygon);
    });
    data.areas[selected].points.forEach(([lat, lng], index) => {
      const marker = new kakao.maps.Marker({ map: kakaoMap, position: new kakao.maps.LatLng(lat, lng), draggable: true });
      kakao.maps.event.addListener(marker, 'click', () => { selectedPoint = index; syncControls(); hint(`${index + 1}번째 점을 선택했습니다.`); });
      kakao.maps.event.addListener(marker, 'dragend', () => {
        const point = marker.getPosition(); data.areas[selected].points[index] = [Number(point.getLat().toFixed(5)), Number(point.getLng().toFixed(5))];
        selectedPoint = index; renderKakao(); syncControls(); hint('경계를 수정했습니다. 임시 저장하거나 설정 파일을 다운로드하세요.');
      });
      markers.push(marker);
    });
    $('editorMapStatus').textContent = '카카오맵 편집';
  }

  function render() { syncControls(); if (kakaoMap) renderKakao(); else renderSvg(); }
  function selectArea(index) { selected = index; selectedPoint = -1; addMode = false; render(); hint(`${data.areas[index].name} 범위를 편집 중입니다.`); }
  function addPoint(point) {
    data.areas[selected].points.push(point.map(value => Number(value.toFixed(5))));
    selectedPoint = data.areas[selected].points.length - 1; addMode = false; render();
    hint('점을 추가했습니다. 필요한 위치로 끌어 이동하세요.');
  }
  function loadKakao(key) {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.onload = () => window.kakao?.maps?.load(() => {
      mapHost.replaceChildren();
      kakaoMap = new kakao.maps.Map(mapHost, { center: new kakao.maps.LatLng(...data.center), level: data.level });
      kakaoMap.setMinLevel(7); kakaoMap.setMaxLevel(12);
      kakao.maps.event.addListener(kakaoMap, 'click', event => { if (addMode) addPoint([event.latLng.getLat(), event.latLng.getLng()]); });
      render();
    });
    script.onerror = () => notice('카카오맵을 불러오지 못했습니다. 미리보기 도형으로 편집할 수 있습니다.');
    document.head.append(script);
  }

  async function start() {
    try {
      published = await fetch('/data/service-areas.json', { cache: 'no-store' }).then(response => response.json());
      if (!valid(published)) throw new Error('invalid');
      data = copy(published);
      try { const draft = JSON.parse(localStorage.getItem(storageKey)); if (valid(draft)) { data = draft; notice('이 브라우저에 저장된 임시 작업을 불러왔습니다.'); } } catch {}
      render();
      const config = await fetch('/api/map-config', { cache: 'no-store' }).then(response => response.ok ? response.json() : {}).catch(() => ({}));
      if (config.key) loadKakao(config.key);
    } catch { notice('지역 설정을 불러오지 못했습니다. 홈페이지로 돌아가 다시 열어 주세요.'); }
  }

  $('areaSelect').addEventListener('change', event => selectArea(Number(event.target.value)));
  $('areaName').addEventListener('change', event => { const value = event.target.value.trim(); if (!value) { event.target.value = data.areas[selected].name; return; } data.areas[selected].name = value; render(); });
  $('areaColor').addEventListener('input', event => { data.areas[selected].color = event.target.value; render(); });
  $('addPoint').addEventListener('click', () => { addMode = !addMode; syncControls(); hint(addMode ? '지도 위 원하는 곳을 눌러 점을 추가하세요.' : '점 추가를 취소했습니다.'); });
  $('removePoint').addEventListener('click', () => { if (selectedPoint < 0 || data.areas[selected].points.length <= 3) return; data.areas[selected].points.splice(selectedPoint, 1); selectedPoint = -1; render(); });
  $('addArea').addEventListener('click', () => {
    const name = $('newAreaName').value.trim(); if (!name || data.areas.length >= 30) { notice('새 지역 이름을 입력해 주세요.'); return; }
    const [lat, lng] = data.center;
    data.areas.push({ name, color: '#e94743', points: [[lat+.02,lng-.025],[lat+.02,lng+.025],[lat-.02,lng+.025],[lat-.02,lng-.025]] });
    $('newAreaName').value = ''; selectArea(data.areas.length - 1);
  });
  $('deleteArea').addEventListener('click', () => { if (data.areas.length <= 1) return; data.areas.splice(selected, 1); selectArea(Math.max(0, selected - 1)); });
  $('saveDraft').addEventListener('click', () => { if (!valid(data)) return notice('도형을 확인해 주세요.'); localStorage.setItem(storageKey, JSON.stringify(data)); notice('이 브라우저에 임시 저장했습니다. 사이트 방문자에게는 아직 반영되지 않습니다.'); });
  $('downloadAreas').addEventListener('click', () => {
    if (!valid(data)) return notice('도형을 확인해 주세요.');
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2) + '\n'], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url; link.download = 'service-areas.json'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000); notice('설정 파일을 다운로드했습니다. 파일을 사이트에 적용하면 모든 방문자에게 반영됩니다.');
  });
  $('resetAreas').addEventListener('click', () => { if (!published) return; data = copy(published); selected = 0; selectedPoint = -1; addMode = false; localStorage.removeItem(storageKey); render(); notice('게시된 범위로 되돌렸습니다.'); });
  start();
})();

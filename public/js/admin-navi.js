(() => {
  const input = document.querySelector('#address');
  const status = document.querySelector('#status');
  const find = document.querySelector('#find');
  const open = document.querySelector('#open');
  try { input.value = decodeURIComponent(location.hash.slice(1)); } catch { /* Empty address. */ }
  history.replaceState(null, '', location.pathname);
  let geocoder, destination, revision = 0;
  const script = (src, integrity) => new Promise((resolve, reject) => {
    const tag = document.createElement('script');
    tag.src = src;
    if (integrity) { tag.integrity = integrity; tag.crossOrigin = 'anonymous'; }
    tag.onload = resolve; tag.onerror = reject;
    document.head.append(tag);
  });
  function clearResult() { destination = undefined; open.hidden = true; }
  input.addEventListener('input', () => { revision++; clearResult(); status.textContent = ''; });
  const lookup = (address) => new Promise((resolve, reject) => {
    geocoder.addressSearch(address, (rows, result) => {
      if (result === kakao.maps.services.Status.ERROR) reject(new Error('lookup'));
      else resolve(result === kakao.maps.services.Status.OK ? rows : []);
    }, { analyze_type: kakao.maps.services.AnalyzeType.EXACT, size: 2 });
  });
  async function search() {
    if (!geocoder || !input.value.trim()) return;
    const current = ++revision;
    clearResult(); status.textContent = '주소를 확인하는 중…';
    const address = input.value.trim().replace(/^\(?\d{5}\)?\s+/, '').replace(/\s+/g, ' ');
    const road = address.match(/^(.+?(?:로|길)\s+\d+(?:-\d+)?)(?=\s|[,（(]|$)/)?.[1];
    const lot = address.match(/^(.+?(?:동|리|가)\s+(?:산\s*)?\d+(?:-\d+)?)(?=\s|[,（(]|$)/)?.[1];
    try {
      const rows = await lookup(road || lot || address);
      if (current !== revision) return;
      const row = rows.length === 1 ? rows[0] : null;
      const match = row?.road_address || (row?.address_type === 'REGION_ADDR' ? row.address : null);
      const x = Number(row?.x), y = Number(row?.y);
      if (!match?.address_name || !(x >= 124 && x <= 132 && y >= 32 && y <= 39)) {
        status.textContent = '정확한 위치를 찾지 못했습니다. 도로명과 건물번호 또는 지번을 확인해 주세요.';
        return;
      }
      destination = { name: match.address_name, x, y, coordType: 'wgs84' };
      status.textContent = match.address_name;
      open.hidden = false;
    } catch { if (current === revision) status.textContent = '주소를 확인하지 못했습니다. 다시 시도해 주세요.'; }
  }
  document.querySelector('#search').addEventListener('submit', (event) => { event.preventDefault(); search(); });
  open.addEventListener('click', () => {
    if (!destination) return;
    try { Kakao.Navi.share(destination); }
    catch { status.textContent = '카카오내비를 열지 못했습니다. 다시 시도해 주세요.'; }
  });
  (async () => {
    try {
      const response = await fetch('/api/map-config', { cache: 'no-store', signal: AbortSignal.timeout(8000) });
      const { key } = await response.json();
      if (!response.ok || !key) throw new Error('configuration');
      await script('https://t1.kakaocdn.net/kakao_js_sdk/2.8.5/kakao.min.js', 'sha384-v6JKY3z4FUkWq21oZDHZe74YvuPk/WUrVLrDHTBojHXClOSa3iXy7sCXPY1jkFbj');
      Kakao.init(key);
      await script('https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&libraries=services&appkey=' + encodeURIComponent(key));
      await new Promise(resolve => kakao.maps.load(resolve));
      geocoder = new kakao.maps.services.Geocoder();
      find.disabled = false;
      status.textContent = '';
      if (input.value.trim()) search();
    } catch { status.textContent = '카카오 지도에 연결하지 못했습니다. 화면을 닫고 다시 열어 주세요.'; }
  })();
})();

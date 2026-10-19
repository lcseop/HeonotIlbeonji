(() => {
  const categories = ['google_ads', 'naver_ads', 'google_search', 'naver_search', 'naver_blog', 'naver_cafe', 'social', 'referral', 'direct', 'unknown'];
  const source = (() => {
    try {
      const url = new URL(location.href), params = url.searchParams;
      const tagged = (params.get('utm_source') || '').toLowerCase();
      const paid = /^(cpc|ppc|paid|paid_search|display|paid_social)$/.test((params.get('utm_medium') || '').toLowerCase());
      let source = '';
      if (['gclid','gbraid','wbraid','gad_source'].some(key => params.has(key)) || (tagged.includes('google') && paid)) source = 'google_ads';
      else if (['n_media','n_campaign','n_ad_group','n_ad'].some(key => params.has(key)) || (tagged.includes('naver') && paid)) source = 'naver_ads';
      else if (tagged.includes('blog')) source = 'naver_blog';
      else if (tagged.includes('cafe')) source = 'naver_cafe';
      else if (tagged.includes('naver')) source = 'naver_search';
      else if (tagged.includes('google')) source = 'google_search';
      else if (/facebook|instagram|kakao|daangn|social/.test(tagged)) source = 'social';
      if (!source && document.referrer) {
        const host = new URL(document.referrer).hostname;
        if (host === 'blog.naver.com' || host === 'm.blog.naver.com') source = 'naver_blog';
        else if (host === 'cafe.naver.com' || host === 'm.cafe.naver.com') source = 'naver_cafe';
        else if (/(^|\.)naver\.com$/.test(host)) source = 'naver_search';
        else if (/(^|\.)google\.(com|co\.kr)$/.test(host)) source = 'google_search';
        else if (/facebook|instagram|kakao|daangn/.test(host)) source = 'social';
        else if (host !== url.hostname) source = 'referral';
      }
      if (!source && tagged) source = 'referral';
      if (!source) {
        try { const previous = JSON.parse(sessionStorage.getItem('heonot-traffic') || 'null');
          if (previous && Date.now() >= previous.at && Date.now() - previous.at < 30 * 60000 && categories.includes(previous.source)) source = previous.source;
        } catch { /* Storage is optional. */ }
      }
      source ||= 'direct';
      try { sessionStorage.setItem('heonot-traffic', JSON.stringify({ source, at: Date.now() })); } catch { /* Optional. */ }
      return source;
    } catch { return 'unknown'; }
  })();
  globalThis.heonotTrafficSource = () => source;
  function visitor() {
    try {
      const day = new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);
      let value = JSON.parse(localStorage.getItem('heonot-daily-visitor') || 'null');
      if (!value || value.day !== day || !/^[a-f0-9-]{36}$/i.test(value.id)) value = { day, id: crypto.randomUUID() };
      localStorage.setItem('heonot-daily-visitor', JSON.stringify(value));
      return value.id;
    } catch { return undefined; }
  }
  const send = (type) => {
    try {
      const body = JSON.stringify({ id: crypto.randomUUID(), type, source, ...(type === 'view' ? { visitor: visitor() } : {}) });
      try {
        if (navigator.sendBeacon?.('/api/website-events', new Blob([body], { type: 'application/json' }))) return;
      } catch { /* Fall back when a browser blocks Beacon but permits fetch. */ }
      fetch('/api/website-events', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body, keepalive: true }).catch(() => {});
    } catch { /* Counting must never interrupt the website or dialer. */ }
  };
  if (document.prerendering) document.addEventListener('prerenderingchange', () => send('view'), { once: true });
  else send('view');
  let lastPhoneClick = 0;
  document.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey ||
        !event.target?.closest?.('a[href^="tel:"]')) return;
    const now = Date.now();
    if (now - lastPhoneClick < 1000) return;
    lastPhoneClick = now;
    send('phone');
  }, true);
})();

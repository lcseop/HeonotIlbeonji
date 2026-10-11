(() => {
  const send = (type) => {
    try {
      const body = JSON.stringify({ id: crypto.randomUUID(), type });
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

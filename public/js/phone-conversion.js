(() => {
  const destination = document.querySelector('meta[name="google-ads-phone-click-conversion"]')?.content.trim();
  // Do not mix phone clicks into the existing accepted-pickup conversion.
  if (!/^AW-18497578171\/[A-Za-z0-9_-]+$/.test(destination || '') ||
      destination === 'AW-18497578171/Ftq6CNK4l5QdELvJqvRE') return;

  let waiting = false;
  document.addEventListener('click', (event) => {
    const link = event.target?.closest?.('a[href^="tel:"]');
    if (!link || event.defaultPrevented || event.button !== 0 ||
        event.ctrlKey || event.metaKey || event.shiftKey || event.altKey ||
        typeof window.gtag !== 'function') return;

    event.preventDefault();
    if (waiting) return;
    waiting = true;
    const phoneUrl = link.getAttribute('href');
    let opened = false;
    const dial = () => {
      if (opened) return;
      opened = true;
      waiting = false;
      clearTimeout(fallback);
      window.location.assign(phoneUrl);
    };
    // Open the dialer even when tracking is blocked or its callback never fires.
    const fallback = setTimeout(dial, 1000);
    try {
      window.gtag('event', 'conversion', {
        send_to: destination,
        value: 1.0,
        currency: 'KRW',
        event_callback: dial,
        event_timeout: 800,
      });
    } catch {
      dial();
    }
  });
})();

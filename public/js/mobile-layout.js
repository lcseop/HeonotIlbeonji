(() => {
  const mobile = window.matchMedia('(max-width: 760px)');
  const cards = [...document.querySelectorAll('.guide-card')];
  const chosen = new Map();
  const setOpen = (card, open) => {
    const button = card.querySelector('.guide-toggle');
    const body = card.querySelector('.guide-body');
    if (!button || !body) return;
    button.setAttribute('aria-expanded', String(open));
    button.disabled = !mobile.matches;
    body.hidden = !open;
    const hint = card.querySelector('.guide-badge');
    if (hint) hint.textContent = open ? '박스 눌러 접기' : '박스 눌러 상세 보기';
  };
  const sync = () => cards.forEach(card => setOpen(card, !mobile.matches || chosen.get(card) === true));
  cards.forEach(card => {
    card.addEventListener('click', event => {
      if (!mobile.matches) return;
      if (event.target.closest('.guide-body, a, input, select, textarea')) return;
      const open = card.querySelector('.guide-toggle').getAttribute('aria-expanded') !== 'true';
      chosen.set(card, open); setOpen(card, open);
    });
    card.addEventListener('pickup-guide-open', () => { chosen.set(card, true); setOpen(card, true); });
  });
  document.querySelectorAll('.mobile-more').forEach(button => {
    const content = document.getElementById(button.getAttribute('aria-controls'));
    if (!content) return;
    button.addEventListener('click', () => {
      const open = !content.classList.contains('is-expanded');
      content.classList.toggle('is-expanded', open);
      button.setAttribute('aria-expanded', String(open));
      button.textContent = open ? '간단히 보기 −' : button.dataset.moreLabel;
      if (!open) content.scrollIntoView({ behavior: 'auto', block: 'start' });
    });
  });
  sync(); mobile.addEventListener('change', sync);
  document.documentElement.classList.add('mobile-ready');
})();

const revealElements = document.querySelectorAll('.reveal');
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
const pickupRoute = document.querySelector('.pickup-route');

// Content stays visible without JavaScript or when reduced motion is requested.
if ('IntersectionObserver' in window && !motionPreference.matches) {
  document.querySelectorAll('.item-grid, .gallery-grid, .process-list, .hero-grid').forEach((group) => {
    const columns = getComputedStyle(group).gridTemplateColumns.split(' ').length;
    group.querySelectorAll(':scope > .reveal').forEach((element, index) => {
      element.style.setProperty('--reveal-delay', `${(index % columns) * 90}ms`);
    });
  });

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });
  revealElements.forEach((element) => observer.observe(element));
  document.documentElement.classList.add('motion-ready');

  const routeObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('has-arrived');
        routeObserver.unobserve(entry.target);
      }
    });
  }, { threshold: 1 });
  if (pickupRoute) routeObserver.observe(pickupRoute);

  motionPreference.addEventListener('change', (event) => {
    if (!event.matches) return;
    observer.disconnect();
    routeObserver.disconnect();
    document.documentElement.classList.remove('motion-ready');
    revealElements.forEach((element) => element.classList.add('visible'));
    pickupRoute?.classList.add('has-arrived');
  });
} else {
  revealElements.forEach((element) => element.classList.add('visible'));
  pickupRoute?.classList.add('has-arrived');
}

document.querySelectorAll('.faq-item button').forEach((button) => {
  button.addEventListener('click', () => {
    const item = button.closest('.faq-item');
    const wasOpen = item.classList.contains('open');

    document.querySelectorAll('.faq-item').forEach((faq) => {
      faq.classList.remove('open');
      faq.querySelector('button').setAttribute('aria-expanded', 'false');
      faq.querySelector('i').textContent = '+';
    });

    if (!wasOpen) {
      item.classList.add('open');
      button.setAttribute('aria-expanded', 'true');
      button.querySelector('i').textContent = '−';
    }
  });
});

const modal = document.querySelector('#privacyModal');
const privacyButton = document.querySelector('#privacyButton');
const closeButtons = modal.querySelectorAll('.modal-close, .modal-confirm');

function toggleModal(open) {
  modal.classList.toggle('open', open);
  modal.setAttribute('aria-hidden', String(!open));
  document.body.style.overflow = open ? 'hidden' : '';
  if (open) modal.querySelector('.modal-close').focus();
}

privacyButton.addEventListener('click', () => toggleModal(true));
closeButtons.forEach((button) => button.addEventListener('click', () => toggleModal(false)));
modal.addEventListener('click', (event) => {
  if (event.target === modal) toggleModal(false);
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') toggleModal(false);
});

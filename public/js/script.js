const revealElements = document.querySelectorAll('.reveal');

if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });
  revealElements.forEach((element) => observer.observe(element));
} else {
  revealElements.forEach((element) => element.classList.add('visible'));
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

const dateInput = document.querySelector('input[type="date"]');
if (dateInput) {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  dateInput.min = tomorrow.toISOString().split('T')[0];
}

const form = document.querySelector('#pickupForm');
const formNotice = document.querySelector('#formNotice');
form.addEventListener('submit', (event) => {
  event.preventDefault();
  const selectedDate = new Date(`${dateInput.value}T12:00:00`);
  if (selectedDate.getDay() === 0) {
    formNotice.textContent = '일요일은 운영하지 않습니다. 월요일부터 토요일 사이의 날짜를 선택해 주세요.';
    dateInput.focus();
    return;
  }
  const name = new FormData(form).get('name');
  formNotice.textContent = `${name}님, 프로토타입 신청이 확인되었습니다. 실제 접수 기능은 다음 단계에서 연결할 수 있어요.`;
  form.reset();
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

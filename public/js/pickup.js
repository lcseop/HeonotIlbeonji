(() => {
  const form = document.querySelector('#pickupForm');
  if (!form) return;
  const notice = document.querySelector('#formNotice');
  const button = form.querySelector('[type="submit"]');
  const date = form.elements.date;
  let enabled = false;
  let pending = false;
  let settled = false;
  let token = '';
  let widgetId;
  const fallback = '현재 온라인 접수가 어렵습니다. 010-4880-8259로 전화해 주세요.';

  function updateDateRange() {
    const today = new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);
    const start = new Date(`${today}T00:00:00Z`).getTime();
    date.min = new Date(start + 86400000).toISOString().slice(0, 10);
    date.max = new Date(start + 90 * 86400000).toISOString().slice(0, 10);
  }
  updateDateRange();
  date.addEventListener('focus', updateDateRange);

  function show(message, error = false) {
    notice.textContent = message;
    notice.classList.toggle('error', error);
  }
  function resetChallenge() {
    token = '';
    if (widgetId !== undefined && window.turnstile) window.turnstile.reset(widgetId);
  }

  async function initialize() {
    try {
      const response = await fetch('/api/pickup', { cache: 'no-store', signal: AbortSignal.timeout(8000) });
      if (!response.ok) throw new Error('unavailable');
      const config = await response.json();
      if (!config.enabled || !config.siteKey) throw new Error('unavailable');
      await new Promise((resolve, reject) => {
        const script = document.createElement('script');
        const timeout = setTimeout(() => reject(new Error('timeout')), 12000);
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.onload = () => { clearTimeout(timeout); resolve(); };
        script.onerror = () => { clearTimeout(timeout); reject(new Error('load')); };
        document.head.append(script);
      });
      widgetId = window.turnstile.render('#pickupChallenge', {
        sitekey: config.siteKey, action: 'pickup-request', theme: 'light', size: 'flexible', language: 'ko',
        callback: (value) => { token = value; },
        'expired-callback': () => { token = ''; },
        'error-callback': () => { token = ''; if (!pending && !settled) show('자동 입력 방지 확인을 불러오지 못했습니다. 새로고침하거나 전화로 문의해 주세요.', true); },
      });
      enabled = true;
      button.disabled = false;
      show(config.delivery === 'dashboard' ? '신청 내용은 관리자 신청함으로 전달됩니다.' : '신청 내용은 담당자에게 문자로 전달됩니다.');
    } catch { show(fallback, true); }
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!enabled || pending || settled) return;
    updateDateRange();
    if (!form.reportValidity()) return;
    if (new Date(`${date.value}T00:00:00Z`).getUTCDay() === 0) {
      show('일요일은 운영하지 않습니다. 월요일부터 토요일 사이의 날짜를 선택해 주세요.', true);
      date.focus();
      return;
    }
    if (!token) { show('자동 입력 방지 확인을 완료한 뒤 신청해 주세요.', true); return; }
    const fields = new FormData(form);
    const payload = Object.fromEntries(['name', 'phone', 'address', 'amount', 'date', 'message'].map(key => [key, fields.get(key)]));
    payload.consent = form.elements.consent.checked;
    payload.token = token;
    pending = true;
    button.disabled = true;
    button.textContent = '신청 내용을 전달하고 있어요…';
    form.setAttribute('aria-busy', 'true');
    show('잠시만 기다려 주세요.');
    try {
      const response = await fetch('/api/pickup', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: AbortSignal.timeout(30000) });
      const result = await response.json();
      if (response.ok && result.accepted === true) {
        settled = true;
        form.reset();
        token = '';
        window.turnstile.remove(widgetId);
        show(result.message);
        button.textContent = '신청 전달 완료';
      } else {
        settled = result.uncertain === true;
        show(result.message || fallback, true);
        if (!settled) resetChallenge();
      }
    } catch {
      settled = true;
      show('접수 결과를 확인하지 못했습니다. 작성 내용은 유지됩니다. 중복 신청하지 마시고 010-4880-8259로 확인해 주세요.', true);
    } finally {
      pending = false;
      form.setAttribute('aria-busy', 'false');
      button.disabled = settled;
      if (!settled) button.textContent = '수거 신청하기';
      else if (notice.classList.contains('error')) button.textContent = '전화로 접수 여부를 확인해 주세요';
    }
  });
  initialize();
})();

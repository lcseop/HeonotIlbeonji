const ui = Object.fromEntries([
  'blogId', 'categoryNo', 'findText', 'replaceText', 'matchCase',
  'scanButton', 'nextButton', 'downloadButton',
  'statusTitle', 'statusMessage', 'progressText', 'progressBar', 'scannedCount',
  'matchedCount', 'occurrenceCount', 'successCount', 'failureCount', 'resultBody',
].map((id) => [id, document.getElementById(id)]));

let posts = [];
let matchedPosts = [];
let workerTabId = null;
let awaitingManualPublish = false;

restoreSettings();

ui.scanButton.addEventListener('click', scanPosts);
ui.nextButton.addEventListener('click', prepareNextPost);
ui.downloadButton.addEventListener('click', downloadCsv);
ui.findText.addEventListener('input', saveSettings);
ui.replaceText.addEventListener('input', saveSettings);
ui.matchCase.addEventListener('change', saveSettings);

async function restoreSettings() {
  const saved = await chrome.storage.local.get(['findText', 'replaceText', 'matchCase']);
  ui.findText.value = saved.findText ?? '';
  ui.replaceText.value = saved.replaceText ?? '';
  ui.matchCase.checked = saved.matchCase ?? true;
}

function saveSettings() {
  chrome.storage.local.set({
    findText: ui.findText.value,
    replaceText: ui.replaceText.value,
    matchCase: ui.matchCase.checked,
  });
}

async function scanPosts() {
  const search = ui.findText.value;
  if (!search) return setStatus('입력 필요', '찾을 문자를 먼저 입력해주세요.');

  resetRunState();
  setBusy(true);
  setStatus('번호가 있는 글 검색 중', '네이버 블로그 검색 결과만 읽고 있습니다. 글은 수정하지 않습니다.');

  try {
    const seen = new Set();
    let emptyPages = 0;
    let expectedMatches = 0;
    let checkedSearchResults = 0;

    for (let page = 1; page <= 30; page += 1) {
      const url = `https://blog.naver.com/PostSearchList.naver?blogId=${encodeURIComponent(ui.blogId.value)}&categoryNo=${encodeURIComponent(ui.categoryNo.value)}&SearchText=${encodeURIComponent(search)}&range=blog&orderBy=date&cpage=${page}`;
      const response = await fetch(url, { credentials: 'include' });
      if (!response.ok) throw new Error(`검색 결과 ${page}페이지를 불러오지 못했습니다. (${response.status})`);
      const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
      if (page === 1) {
        const countMatch = (doc.body?.textContent || '').match(/검색결과\s*([\d,]+)건/);
        expectedMatches = countMatch ? Number(countMatch[1].replaceAll(',', '')) : 0;
      }
      const pagePosts = extractSearchPosts(doc, ui.blogId.value).filter((post) => !seen.has(post.logNo));
      checkedSearchResults += pagePosts.length;

      if (pagePosts.length === 0) {
        emptyPages += 1;
        if (emptyPages >= 2) break;
      } else {
        emptyPages = 0;
      }

      const verifiedPosts = await Promise.all(pagePosts.map((post) => verifySearchPost(
        post,
        search,
        ui.matchCase.checked,
        ui.blogId.value,
      )));
      for (const post of verifiedPosts.filter(Boolean)) {
        seen.add(post.logNo);
        posts.push(post);
      }

      updateStats();
      updateProgress(checkedSearchResults, expectedMatches || checkedSearchResults || 1);
      setStatus('번호가 있는 글 검색 중', `${page}페이지까지 실제 게시글을 확인했습니다. 현재 ${posts.length}개 유효한 대상 글을 찾았습니다.`);
      await sleep(250);
      if (expectedMatches > 0 && page >= Math.ceil(expectedMatches / 10)) break;
    }

    matchedPosts = posts.slice();
    renderResults();
    updateStats();
    updateProgress(posts.length, posts.length || 1);
    ui.downloadButton.disabled = posts.length === 0;
    ui.nextButton.disabled = matchedPosts.length === 0;
    setStatus(
      '검사 완료',
      `네이버 검색에서 기존 번호가 확인된 ${matchedPosts.length}개 글만 작업 대상으로 준비했습니다. 아직 어떤 글도 수정하지 않았습니다.`,
    );
  } catch (error) {
    setStatus('검사 실패', error.message || '글 목록을 검사하지 못했습니다.');
  } finally {
    setBusy(false);
  }
}

async function verifySearchPost(post, search, caseSensitive, blogId) {
  try {
    const url = `https://blog.naver.com/PostView.naver?blogId=${encodeURIComponent(blogId)}&logNo=${post.logNo}`;
    const response = await fetch(url, { credentials: 'include' });
    if (!response.ok) return null;
    const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
    const container = doc.getElementById(`post-view${post.logNo}`);
    if (!container) return null;
    const occurrences = countLiteral(container.textContent || '', search, caseSensitive);
    if (occurrences === 0) return null;
    return { ...post, occurrences };
  } catch {
    return null;
  }
}

function extractSearchPosts(doc, blogId) {
  const found = new Map();
  for (const link of doc.querySelectorAll('a.s_link[href*="logNo="]')) {
    const href = link.getAttribute('href') || '';
    let logNo = null;
    try {
      const url = new URL(href, 'https://blog.naver.com');
      logNo = url.searchParams.get('logNo');
      if (!logNo) {
        const pathMatch = url.pathname.match(new RegExp(`/${blogId}/(\\d+)$`));
        logNo = pathMatch?.[1] || null;
      }
    } catch {
      continue;
    }
    if (!/^\d+$/.test(logNo || '')) continue;
    const text = (link.textContent || '').replace(/\s+/g, ' ').trim();
    const current = found.get(logNo);
    if (!current || text.length > current.title.length) {
      found.set(logNo, {
        logNo,
        title: text || `게시글 ${logNo}`,
        occurrences: 1,
        state: '대기',
        message: '',
      });
    }
  }
  return [...found.values()];
}

async function prepareNextPost() {
  if (awaitingManualPublish) {
    const published = confirm('이전 작업 탭에서 네이버 발행 버튼을 직접 눌렀나요?\n\n발행했다면 확인, 아직이라면 취소를 누르세요.');
    if (!published) return;
    awaitingManualPublish = false;
  }
  const post = matchedPosts.find((item) => item.state === '대기');
  if (!post) return setStatus('순차 작업 완료', '더 이상 대기 중인 대상 글이 없습니다. 결과 CSV를 저장해 확인해주세요.');

  setBusy(true);
  const succeeded = await processPost(post);
  ui.downloadButton.disabled = false;
  setBusy(false);
  updateProgress(
    matchedPosts.filter((item) => ['변경완료', '실패'].includes(item.state)).length,
    matchedPosts.length,
  );
  const remaining = matchedPosts.filter((item) => item.state === '대기').length;
  ui.nextButton.disabled = remaining === 0;

  if (succeeded) {
    awaitingManualPublish = true;
    setStatus('문자 변경 완료 · 발행 필요', `작업 탭에서 변경 내용을 확인하고 발행을 직접 눌러주세요. 남은 글: ${remaining}개`);
  } else {
    awaitingManualPublish = false;
    setStatus('이 글은 실패로 기록됨', `실패한 글은 건너뛰었습니다. ‘다음 글 열고 문자 바꾸기’를 눌러 계속할 수 있습니다. 남은 글: ${remaining}개`);
  }
}

async function processPost(post) {
  post.state = '진행';
  post.message = '';
  renderResults();
  setStatus('글 수정 중', post.title);

  try {
    const editUrl = `https://blog.naver.com/PostUpdateForm.naver?blogId=${encodeURIComponent(ui.blogId.value)}&logNo=${post.logNo}`;
    if (workerTabId !== null) {
      try {
        await chrome.tabs.get(workerTabId);
      } catch {
        workerTabId = null;
      }
    }

    if (workerTabId === null) {
      const tab = await chrome.tabs.create({ url: editUrl, active: false });
      workerTabId = tab.id;
    } else {
      await chrome.tabs.update(workerTabId, { url: editUrl, active: false });
    }

    const loadedTab = await waitForTabLoad(workerTabId, 45000);
    if (loadedTab.url?.includes('nid.naver.com')) {
      await chrome.tabs.update(workerTabId, { active: true });
      throw new Error('네이버 로그인이 필요합니다. 작업 탭에서 로그인한 뒤 다시 시도해주세요.');
    }

    await chrome.tabs.update(workerTabId, { active: true });
    await sleep(500);

    let changed = 0;
    let lastDiagnostics = [];
    const debuggerTarget = { tabId: workerTabId };
    for (let index = 0; index < Math.min(20, Math.max(1, post.occurrences)); index += 1) {
      let selectedInfo = null;
      const selectionDeadline = Date.now() + (changed === 0 ? 30000 : 2500);
      while (Date.now() < selectionDeadline) {
        const selectResults = await chrome.scripting.executeScript({
          target: { tabId: workerTabId, allFrames: true },
          func: selectBlogEditorText,
          args: [ui.findText.value, ui.matchCase.checked],
        });
        lastDiagnostics = selectResults.map((result) => result.result).filter(Boolean);
        selectedInfo = lastDiagnostics.find((item) => item.selected && item.drag) || null;
        if (selectedInfo) break;
        await sleep(500);
      }
      if (!selectedInfo) break;

      try {
        await chrome.debugger.attach(debuggerTarget, '1.3');
        await dispatchDebuggerDrag(debuggerTarget, selectedInfo.drag);

        const selectedTextResults = await chrome.scripting.executeScript({
          target: { tabId: workerTabId, allFrames: true },
          func: () => document.getSelection()?.toString() || '',
        });
        const expectedSelection = ui.matchCase.checked
          ? ui.findText.value
          : ui.findText.value.toLocaleLowerCase('ko-KR');
        const exactSelectionExists = selectedTextResults.some((result) => {
          const value = ui.matchCase.checked
            ? result.result
            : result.result.toLocaleLowerCase('ko-KR');
          return value === expectedSelection;
        });
        if (!exactSelectionExists) {
          throw new Error('기존 번호 전체가 정확히 선택되지 않아 변경하지 않고 건너뜁니다.');
        }

        await dispatchDebuggerKey(debuggerTarget, 'Backspace', 'Backspace', 8, 0);
        await chrome.debugger.sendCommand(debuggerTarget, 'Input.insertText', {
          text: ui.replaceText.value,
        });
        changed += 1;
      } catch (error) {
        if (/Another debugger|already attached|Cannot attach/i.test(error.message || '')) {
          throw new Error('작업 탭의 개발자 도구를 닫은 뒤 다시 시도해주세요. 실제 입력 기능을 연결할 수 없습니다.');
        }
        throw error;
      } finally {
        try {
          await chrome.debugger.detach(debuggerTarget);
        } catch {
          // 연결되지 않았거나 이미 해제된 경우입니다.
        }
      }
      await sleep(500);
    }

    if (changed === 0) {
      const diagnostics = lastDiagnostics;
      const editorCount = diagnostics.reduce((sum, item) => sum + (item.editors || 0), 0);
      const editableCount = diagnostics.reduce((sum, item) => sum + (item.editableCount || 0), 0);
      const bodyMatchFrames = diagnostics.filter((item) => item.bodyHasSearch).length;
      const iframeCount = diagnostics.reduce((sum, item) => sum + (item.iframeCount || 0), 0);
      const renderedMatches = diagnostics.reduce((sum, item) => sum + (item.renderedMatchCount || 0), 0);
      throw new Error(`수정 화면에서 일치 문자를 찾지 못했습니다. [진단: 접근 프레임 ${diagnostics.length}, 편집 후보 ${editorCount}/${editableCount}, 화면 내 문자 ${bodyMatchFrames}, 편집 블록 ${renderedMatches}, 내부 프레임 ${iframeCount}]`);
    }

    post.state = '변경완료';
    post.message = `${changed}곳 변경 · 사용자가 발행해야 함`;
    await chrome.tabs.update(workerTabId, { active: true });
    updateStats();
    renderResults();
    return true;
  } catch (error) {
    post.state = '실패';
    post.message = error.message || '알 수 없는 오류';
    if (workerTabId !== null) {
      try {
        await chrome.tabs.update(workerTabId, { active: true });
      } catch {
        workerTabId = null;
      }
    }
    updateStats();
    renderResults();
    setStatus('글 처리 실패', `${post.title}: ${post.message}`);
    return false;
  }
}

async function dispatchDebuggerKey(target, key, code, windowsVirtualKeyCode, modifiers) {
  const common = { key, code, windowsVirtualKeyCode, nativeVirtualKeyCode: windowsVirtualKeyCode, modifiers };
  await chrome.debugger.sendCommand(target, 'Input.dispatchKeyEvent', { type: 'rawKeyDown', ...common });
  await chrome.debugger.sendCommand(target, 'Input.dispatchKeyEvent', { type: 'keyUp', ...common });
}

async function dispatchDebuggerDrag(target, drag) {
  await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
    type: 'mousePressed', x: drag.startX, y: drag.startY, button: 'left', buttons: 1, clickCount: 1,
  });
  for (let step = 1; step <= 5; step += 1) {
    const ratio = step / 5;
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: drag.startX + (drag.endX - drag.startX) * ratio,
      y: drag.startY + (drag.endY - drag.startY) * ratio,
      button: 'left',
      buttons: 1,
    });
  }
  await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
    type: 'mouseReleased', x: drag.endX, y: drag.endY, button: 'left', buttons: 0, clickCount: 1,
  });
}

async function waitForTabLoad(tabId, timeoutMs) {
  const current = await chrome.tabs.get(tabId);
  if (current.status === 'complete') return current;

  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      chrome.tabs.onUpdated.removeListener(listener);
      reject(new Error('수정 화면을 불러오는 시간이 초과됐습니다.'));
    }, timeoutMs);
    const listener = (updatedId, changeInfo, tab) => {
      if (updatedId !== tabId || changeInfo.status !== 'complete') return;
      clearTimeout(timeout);
      chrome.tabs.onUpdated.removeListener(listener);
      resolve(tab);
    };
    chrome.tabs.onUpdated.addListener(listener);
  });
}

async function selectBlogEditorText(search, caseSensitive) {
  const deadline = Date.now() + 1500;
  const needle = caseSensitive ? search : search.toLocaleLowerCase('ko-KR');
  const originalScrollY = window.scrollY;
  let roots = [];
  let renderedMatchCount = 0;

  while (Date.now() < deadline) {
    const candidates = [...document.querySelectorAll('[contenteditable="true"], [contenteditable="plaintext-only"]')]
      .filter((element) => {
        if (!(element instanceof HTMLElement)) return false;
        const style = getComputedStyle(element);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        return !element.hasAttribute('disabled');
      });
    roots = [...new Set(candidates)].filter((element) => !element.parentElement?.closest('[contenteditable]'));
    const contentIsReady = roots.some((root) => {
      const value = root.textContent || '';
      const source = caseSensitive ? value : value.toLocaleLowerCase('ko-KR');
      return source.includes(needle);
    });
    if (contentIsReady) break;

    const blockSelectors = [
      '.se-component',
      '.se-module-text',
      '.se-text-paragraph',
      '[class*="se-component"]',
      '[class*="se-text"]',
    ].join(',');
    const matchingBlocks = [...document.querySelectorAll(blockSelectors)].filter((element) => {
      if (!(element instanceof HTMLElement)) return false;
      if (element.closest('script, style, noscript')) return false;
      const value = element.textContent || '';
      const source = caseSensitive ? value : value.toLocaleLowerCase('ko-KR');
      return source.includes(needle);
    });
    renderedMatchCount = matchingBlocks.length;

    if (matchingBlocks.length > 0) {
      const target = matchingBlocks
        .slice()
        .sort((left, right) => left.querySelectorAll('*').length - right.querySelectorAll('*').length)[0];
      target.scrollIntoView({ block: 'center', behavior: 'auto' });
      const textRange = findTextRange(target);
      const rect = textRange?.getBoundingClientRect();
      const clientX = rect && rect.width > 0 ? rect.left + rect.width / 2 : target.getBoundingClientRect().left + 8;
      const clientY = rect && rect.height > 0 ? rect.top + rect.height / 2 : target.getBoundingClientRect().top + 8;
      const clickTarget = document.elementFromPoint(clientX, clientY) || target;
      const eventOptions = { bubbles: true, cancelable: true, view: window, clientX, clientY, button: 0 };
      if (typeof PointerEvent === 'function') {
        clickTarget.dispatchEvent(new PointerEvent('pointerdown', { ...eventOptions, pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 1 }));
      }
      clickTarget.dispatchEvent(new MouseEvent('mousedown', { ...eventOptions, buttons: 1 }));
      if (typeof PointerEvent === 'function') {
        clickTarget.dispatchEvent(new PointerEvent('pointerup', { ...eventOptions, pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 0 }));
      }
      clickTarget.dispatchEvent(new MouseEvent('mouseup', { ...eventOptions, buttons: 0 }));
      clickTarget.dispatchEvent(new MouseEvent('click', { ...eventOptions, buttons: 0 }));
      if (textRange?.startContainer?.isConnected) {
        const selection = document.getSelection();
        selection.removeAllRanges();
        selection.addRange(textRange);
        return {
          url: location.href,
          selected: true,
          editors: roots.length,
          editableCount: document.querySelectorAll('[contenteditable]').length,
          bodyHasSearch: true,
          iframeCount: document.querySelectorAll('iframe').length,
          renderedMatchCount,
          drag: dragForRange(textRange),
        };
      }
    } else {
      const nextY = window.scrollY + Math.max(500, Math.floor(window.innerHeight * 0.7));
      if (nextY >= document.documentElement.scrollHeight - window.innerHeight) {
        window.scrollTo({ top: 0, behavior: 'auto' });
      } else {
        window.scrollTo({ top: nextY, behavior: 'auto' });
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 400));
  }

  const diagnostic = () => {
    const bodyValue = document.body?.textContent || '';
    const bodySource = caseSensitive ? bodyValue : bodyValue.toLocaleLowerCase('ko-KR');
    return {
      url: location.href,
      selected: false,
      editors: roots.length,
      editableCount: document.querySelectorAll('[contenteditable]').length,
      bodyHasSearch: bodySource.includes(needle),
      iframeCount: document.querySelectorAll('iframe').length,
      renderedMatchCount,
    };
  };

  if (roots.length === 0) {
    const result = diagnostic();
    window.scrollTo({ top: originalScrollY, behavior: 'auto' });
    return result;
  }
  for (const root of roots) {
    while (root.isConnected) {
      const segments = collectTextSegments(root);
      const value = segments.map((segment) => segment.value).join('');
      const source = caseSensitive ? value : value.toLocaleLowerCase('ko-KR');
      const matchIndex = source.indexOf(needle);
      if (matchIndex < 0) break;

      const matchEnd = matchIndex + search.length;
      const startSegment = segments.find((segment) => matchIndex >= segment.start && matchIndex < segment.end);
      const endSegment = segments.find((segment) => matchEnd > segment.start && matchEnd <= segment.end);
      if (!startSegment || !endSegment) break;

      const range = document.createRange();
      range.setStart(startSegment.node, matchIndex - startSegment.start);
      range.setEnd(endSegment.node, matchEnd - endSegment.start);
      startSegment.node.parentElement?.scrollIntoView({ block: 'center', behavior: 'auto' });
      root.focus();
      const selection = document.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      return {
        url: location.href,
        selected: true,
        editors: roots.length,
        editableCount: document.querySelectorAll('[contenteditable]').length,
        bodyHasSearch: true,
        iframeCount: document.querySelectorAll('iframe').length,
        renderedMatchCount,
        drag: dragForRange(range),
      };
    }
  }

  window.scrollTo({ top: originalScrollY, behavior: 'auto' });
  return diagnostic();

  function collectTextSegments(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        const parent = node.parentElement;
        if (!parent || parent.closest('script, style, noscript')) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      },
    });
    const segments = [];
    let offset = 0;
    while (walker.nextNode()) {
      const node = walker.currentNode;
      const value = node.nodeValue || '';
      segments.push({ node, value, start: offset, end: offset + value.length });
      offset += value.length;
    }
    return segments;
  }

  function findTextRange(element) {
    const segments = collectTextSegments(element);
    const value = segments.map((segment) => segment.value).join('');
    const source = caseSensitive ? value : value.toLocaleLowerCase('ko-KR');
    const matchIndex = source.indexOf(needle);
    if (matchIndex < 0) return null;
    const matchEnd = matchIndex + search.length;
    const startSegment = segments.find((segment) => matchIndex >= segment.start && matchIndex < segment.end);
    const endSegment = segments.find((segment) => matchEnd > segment.start && matchEnd <= segment.end);
    if (!startSegment || !endSegment) return null;
    const range = document.createRange();
    range.setStart(startSegment.node, matchIndex - startSegment.start);
    range.setEnd(endSegment.node, matchEnd - endSegment.start);
    return range;
  }

  function dragForRange(range) {
    const rects = [...range.getClientRects()].filter((rect) => rect.width > 0 && rect.height > 0);
    if (rects.length === 0) return null;
    const first = rects[0];
    const last = rects[rects.length - 1];
    return {
      startX: first.left + 1,
      startY: first.top + first.height / 2,
      endX: last.right - 1,
      endY: last.top + last.height / 2,
    };
  }
}

function renderResults() {
  const rows = posts;
  if (rows.length === 0) {
    ui.resultBody.innerHTML = '<tr><td colspan="4" class="empty">일치하는 글이 없습니다.</td></tr>';
    return;
  }
  ui.resultBody.innerHTML = rows.map((post) => {
    const className = post.state === '변경완료' ? 'success' : post.state === '실패' ? 'failure' : post.state === '진행' ? 'running' : '';
    const url = `https://blog.naver.com/${encodeURIComponent(ui.blogId.value)}/${post.logNo}`;
    const replaceResult = post.state === '변경완료' ? post.message.split(' · ')[0] : '자동';
    return `<tr><td><span class="state ${className}" title="${escapeHtml(post.message)}">${escapeHtml(post.state)}</span></td><td><a href="${url}" target="_blank" rel="noreferrer">${escapeHtml(post.title)}</a></td><td>${escapeHtml(replaceResult)}</td><td>${post.logNo}</td></tr>`;
  }).join('');
}

function updateStats() {
  ui.scannedCount.textContent = posts.length.toLocaleString();
  ui.matchedCount.textContent = posts.length.toLocaleString();
  ui.occurrenceCount.textContent = countState('대기').toLocaleString();
  ui.successCount.textContent = countState('변경완료').toLocaleString();
  ui.failureCount.textContent = countState('실패').toLocaleString();
}

function updateProgress(current, total) {
  const safeTotal = Math.max(1, total);
  ui.progressText.textContent = `${current.toLocaleString()} / ${total.toLocaleString()}`;
  ui.progressBar.style.width = `${Math.min(100, current / safeTotal * 100)}%`;
}

function setStatus(title, message) {
  ui.statusTitle.textContent = title;
  ui.statusMessage.textContent = message;
}

function setBusy(busy) {
  ui.scanButton.disabled = busy;
  ui.nextButton.disabled = busy || !matchedPosts.some((post) => post.state === '대기');
  ui.findText.disabled = busy;
  ui.replaceText.disabled = busy;
}

function resetRunState() {
  posts = [];
  matchedPosts = [];
  awaitingManualPublish = false;
  ui.nextButton.disabled = true;
  updateStats();
  updateProgress(0, 0);
  renderResults();
}

function countState(state) {
  return posts.filter((post) => post.state === state).length;
}

function countLiteral(value, search, caseSensitive) {
  if (!value || !search) return 0;
  const source = caseSensitive ? value : value.toLocaleLowerCase('ko-KR');
  const needle = caseSensitive ? search : search.toLocaleLowerCase('ko-KR');
  let count = 0;
  let index = 0;
  while ((index = source.indexOf(needle, index)) !== -1) {
    count += 1;
    index += Math.max(1, needle.length);
  }
  return count;
}

function downloadCsv() {
  const header = ['상태', '제목', '글 번호', '메시지'];
  const lines = [header, ...posts.map((post) => [post.state, post.title, post.logNo, post.message])];
  const csv = '\uFEFF' + lines.map((row) => row.map(csvCell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `naver-blog-replace-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

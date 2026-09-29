const findText = document.querySelector('#findText');
const replaceText = document.querySelector('#replaceText');
const matchCase = document.querySelector('#matchCase');
const previewButton = document.querySelector('#previewButton');
const replaceButton = document.querySelector('#replaceButton');
const undoButton = document.querySelector('#undoButton');
const statusBox = document.querySelector('#status');
const blogBatchButton = document.querySelector('#blogBatchButton');

restoreSettings();

findText.addEventListener('input', saveSettings);
replaceText.addEventListener('input', saveSettings);
matchCase.addEventListener('change', saveSettings);
previewButton.addEventListener('click', () => runOnEditors('preview'));
replaceButton.addEventListener('click', () => runOnEditors('replace'));
undoButton.addEventListener('click', () => runOnEditors('undo'));
blogBatchButton.addEventListener('click', () => {
  chrome.tabs.create({ url: chrome.runtime.getURL('batch.html') });
});

async function restoreSettings() {
  const saved = await chrome.storage.local.get(['findText', 'replaceText', 'matchCase']);
  findText.value = saved.findText ?? '';
  replaceText.value = saved.replaceText ?? '';
  matchCase.checked = saved.matchCase ?? true;
}

function saveSettings() {
  chrome.storage.local.set({
    findText: findText.value,
    replaceText: replaceText.value,
    matchCase: matchCase.checked,
  });
}

async function runOnEditors(mode) {
  if (mode !== 'undo' && findText.value.length === 0) {
    showStatus('찾을 문자를 입력해주세요.', 'warning');
    findText.focus();
    return;
  }

  setBusy(true);

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id || !isSupportedEditorUrl(tab.url)) {
      throw new Error('네이버 카페 또는 블로그 글 수정 화면에서 실행해주세요.');
    }

    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: processEditableAreas,
      args: [mode, findText.value, replaceText.value, matchCase.checked],
    });

    const summary = results.reduce(
      (total, item) => {
        const value = item.result;
        if (!value) return total;
        total.editors += value.editors ?? 0;
        total.matches += value.matches ?? 0;
        total.changed += value.changed ?? 0;
        total.undone += value.undone ?? 0;
        return total;
      },
      { editors: 0, matches: 0, changed: 0, undone: 0 },
    );

    renderSummary(mode, summary);
  } catch (error) {
    showStatus(error.message || '실행 중 오류가 발생했습니다.', 'error');
  } finally {
    setBusy(false);
  }
}

function renderSummary(mode, summary) {
  if (mode === 'undo') {
    if (summary.undone > 0) {
      showStatus(`${summary.undone.toLocaleString()}곳의 변경을 되돌렸습니다.`, 'success');
    } else {
      showStatus('이 화면에서 되돌릴 변경 내용이 없습니다.', 'warning');
    }
    return;
  }

  if (summary.editors === 0) {
    showStatus('편집 영역을 찾지 못했습니다. 먼저 글의 수정 화면을 열어주세요.', 'warning');
    return;
  }

  if (mode === 'preview') {
    const message = summary.matches > 0
      ? `제목·본문에서 ${summary.matches.toLocaleString()}곳을 찾았습니다.`
      : '제목·본문에서 일치하는 문자를 찾지 못했습니다.';
    showStatus(message, summary.matches > 0 ? 'success' : 'warning');
    return;
  }

  if (summary.changed > 0) {
    showStatus(`${summary.changed.toLocaleString()}곳을 바꿨습니다. 글 내용을 확인해주세요.`, 'success');
  } else {
    showStatus('바꿀 문자를 찾지 못했습니다.', 'warning');
  }
}

function setBusy(isBusy) {
  previewButton.disabled = isBusy;
  replaceButton.disabled = isBusy;
  if (isBusy) showStatus('편집 내용을 확인하고 있습니다…', 'idle');
}

function showStatus(message, type) {
  statusBox.textContent = message;
  statusBox.className = `status ${type}`;
}

function isSupportedEditorUrl(url = '') {
  try {
    const hostname = new URL(url).hostname;
    return hostname === 'cafe.naver.com'
      || hostname === 'm.cafe.naver.com'
      || hostname === 'blog.naver.com';
  } catch {
    return false;
  }
}

function processEditableAreas(mode, search, replacement, caseSensitive) {
  const undoKey = '__naverCafeTextReplacerUndoV1';
  const selector = [
    'textarea',
    'input[type="text"]',
    '[contenteditable]:not([contenteditable="false"])',
  ].join(',');

  const isUsable = (element) => {
    if (!(element instanceof HTMLElement)) return false;
    if (element.matches('[contenteditable]') && element.parentElement?.closest('[contenteditable]')) return false;
    const style = getComputedStyle(element);
    return style.display !== 'none' && style.visibility !== 'hidden' && !element.hasAttribute('disabled');
  };

  const editorCandidates = [...document.querySelectorAll(selector)];
  const isLegacyEditorDocument = document.designMode?.toLowerCase() === 'on';
  if (isLegacyEditorDocument && document.body) editorCandidates.push(document.body);
  if (document.body?.isContentEditable) editorCandidates.push(document.body);

  const editors = [...new Set(editorCandidates)].filter(isUsable);

  if (mode === 'undo') {
    const undoItems = globalThis[undoKey] ?? [];
    let undone = 0;

    for (const item of undoItems) {
      if (!item.target?.isConnected) continue;
      if (item.kind === 'value') {
        setNativeValue(item.target, item.before);
      } else if (item.kind === 'text') {
        item.target.nodeValue = item.before;
        dispatchEditEvents(item.editor);
      }
      undone += item.count;
    }

    globalThis[undoKey] = [];
    return { editors: editors.length, matches: 0, changed: 0, undone };
  }

  const matcher = createMatcher(search, caseSensitive);
  let matches = 0;
  let changed = 0;
  const undoItems = [];

  for (const editor of editors) {
    if (editor instanceof HTMLInputElement || editor instanceof HTMLTextAreaElement) {
      const count = countMatches(editor.value, matcher);
      matches += count;
      if (mode === 'replace' && count > 0) {
        const before = editor.value;
        setNativeValue(editor, replaceMatches(before, matcher, replacement));
        undoItems.push({ kind: 'value', target: editor, before, count });
        changed += count;
      }
      continue;
    }

    const walker = document.createTreeWalker(
      editor,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode(node) {
          const parent = node.parentElement;
          if (!parent || parent.closest('script, style, noscript')) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        },
      },
    );

    const textNodes = [];
    let editorChanged = 0;
    while (walker.nextNode()) textNodes.push(walker.currentNode);

    for (const node of textNodes) {
      const before = node.nodeValue ?? '';
      const count = countMatches(before, matcher);
      matches += count;
      if (mode === 'replace' && count > 0) {
        node.nodeValue = replaceMatches(before, matcher, replacement);
        undoItems.push({ kind: 'text', target: node, editor, before, count });
        changed += count;
        editorChanged += count;
      }
    }

    if (mode === 'replace' && editorChanged > 0) dispatchEditEvents(editor);
  }

  if (mode === 'replace') globalThis[undoKey] = undoItems;
  return { editors: editors.length, matches, changed, undone: 0 };

  function createMatcher(text, shouldMatchCase) {
    const flags = shouldMatchCase ? 'g' : 'gi';
    return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), flags);
  }

  function countMatches(value, regex) {
    if (!value) return 0;
    regex.lastIndex = 0;
    return [...value.matchAll(regex)].length;
  }

  function replaceMatches(value, regex, nextValue) {
    regex.lastIndex = 0;
    return value.replace(regex, () => nextValue);
  }

  function setNativeValue(element, value) {
    const prototype = element instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype
      : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
    setter?.call(element, value);
    dispatchEditEvents(element);
  }

  function dispatchEditEvents(element) {
    element.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

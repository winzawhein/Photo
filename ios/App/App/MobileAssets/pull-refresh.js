(() => {
  if (window.__photoPullRefresh) { window.__photoPullRefresh.reset(); return; }
  const threshold = 96;
  let start = null, distance = 0, busy = false, hint = null;
  const hide = () => { if (hint) hint.remove(); hint = null; };
  const reset = () => { start = null; distance = 0; busy = false; hide(); };
  function atTop(target) {
    if (window.scrollY > 1 || (document.scrollingElement?.scrollTop || 0) > 1) return false;
    for (let node = target; node && node !== document; node = node.parentElement) {
      if (node.nodeType !== 1) continue;
      // A nested scroller belongs to the website, even when WebView.scrollY is zero.
      if (node.scrollTop > 1) return false;
      if (node.matches('input,textarea,select,button,[contenteditable="true"],[role="slider"],dialog,[role="dialog"]')) return false;
    }
    return true;
  }
  document.addEventListener('touchstart', e => {
    start = null; distance = 0; hide();
    if (busy || e.touches.length !== 1 || !atTop(e.target)) return;
    const t = e.touches[0];
    start = { x: t.clientX, y: t.clientY, target: e.target };
  }, { passive: true, capture: true });
  document.addEventListener('touchmove', e => {
    if (!start) return;
    if (e.touches.length !== 1 || !atTop(start.target)) { start = null; hide(); return; }
    const dx = e.touches[0].clientX - start.x;
    const dy = e.touches[0].clientY - start.y;
    if (dy < -8 || Math.abs(dx) > Math.max(12, Math.abs(dy))) { start = null; hide(); return; }
    distance = Math.max(0, dy);
    if (distance < 12) return;
    // Only consume a downward gesture that began at the real top of the page.
    if (!e.cancelable) { start = null; hide(); return; }
    e.preventDefault();
    if (!hint) {
      hint = document.createElement('div');
      hint.setAttribute('role', 'status');
      hint.style.cssText = 'position:fixed;top:12px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#ffffff;color:#137f83;border:1px solid #b9e6e5;border-radius:24px;padding:10px 18px;font:14px system-ui;box-shadow:0 3px 14px #0002;pointer-events:none';
      document.documentElement.appendChild(hint);
    }
    hint.textContent = distance >= threshold ? 'Release to refresh' : 'Pull to refresh';
  }, { passive: false, capture: true });
  document.addEventListener('touchend', () => {
    const trigger = start && distance >= threshold && !busy;
    start = null; distance = 0; hide();
    if (trigger && window.PhotoRefresh) { busy = true; window.PhotoRefresh.postMessage('refresh'); }
  }, { passive: true, capture: true });
  document.addEventListener('touchcancel', reset, { passive: true, capture: true });
  window.__photoPullRefresh = { reset };
})();

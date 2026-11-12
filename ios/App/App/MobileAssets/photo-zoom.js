(() => {
  // Zoom the detail image, never the surrounding app page or gallery thumbnails.
  const selector = '.photo-full-view img.photo-full-image';
  const bound = new WeakSet();
  function attach(img) {
    if (bound.has(img)) return;
    bound.add(img);
    const frame = img.closest('.photo-full-view');
    // Preserve React's DOM ownership and keep close/previous/next controls intact.
    frame.style.setProperty('overflow', 'hidden', 'important');
    img.style.touchAction = 'none';
    img.style.transformOrigin = 'center center';
    img.style.userSelect = 'none';
    img.draggable = false;
    let scale = 1, x = 0, y = 0, gesture = null, lastTap = 0;
    const distance = t => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
    function render() {

      const maxX = img.clientWidth * (scale - 1) / 2;
      const maxY = img.clientHeight * (scale - 1) / 2;
      x = Math.max(-maxX, Math.min(maxX, x));
      y = Math.max(-maxY, Math.min(maxY, y));
      img.style.transform = `translate(${x}px,${y}px) scale(${scale})`;
    }
    function begin(e) {
      if (e.target !== img) { gesture = null; return; }
      gesture = e.touches.length === 2
        ? { distance: distance(e.touches), scale }
        : { startX: e.touches[0].clientX, startY: e.touches[0].clientY, x, y, moved: false };
    }
    frame.addEventListener('touchstart', begin, { passive: true });
    frame.addEventListener('touchmove', e => {
      if (!gesture) return;
      if (e.touches.length === 2) {
        if (!gesture.distance) begin(e);
        if (gesture.distance > 0) scale = Math.max(1, Math.min(5, gesture.scale * distance(e.touches) / gesture.distance));
        lastTap = 0;
      } else if (e.touches.length === 1 && gesture.startX !== undefined) {
        const dx = e.touches[0].clientX - gesture.startX;
        const dy = e.touches[0].clientY - gesture.startY;
        gesture.moved ||= Math.abs(dx) + Math.abs(dy) > 8;
        if (scale === 1) return;
        x = gesture.x + dx; y = gesture.y + dy;
      } else return;
      if (e.cancelable) e.preventDefault();
      render();
    }, { passive: false });
    frame.addEventListener('touchend', e => {
      if (e.touches.length) { begin(e); return; }
      if (gesture && gesture.startX !== undefined && !gesture.moved) {
        const now = Date.now();
        if (now - lastTap < 300) { scale = scale > 1 ? 1 : 2.5; x = y = 0; render(); lastTap = 0; }
        else lastTap = now;
      }
      gesture = null;
    }, { passive: true });
    frame.addEventListener('touchcancel', () => { gesture = null; lastTap = 0; }, { passive: true });
    img.addEventListener('load', () => { scale = 1; x = y = 0; gesture = null; lastTap = 0; render(); });
  }
  function scan() { document.querySelectorAll(selector).forEach(attach); }
  scan();
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
})();

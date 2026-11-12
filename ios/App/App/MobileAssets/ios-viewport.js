(() => {
  const content = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no';
  const head = document.head;
  if (!head) return;
  const apply = () => {
    let metas = head.querySelectorAll('meta[name="viewport"]');
    if (!metas.length) {
      const meta = document.createElement('meta');
      meta.name = 'viewport';
      head.appendChild(meta);
      metas = [meta];
    }
    for (const meta of metas) {
      if (meta.content !== content) meta.content = content;
    }
  };
  apply();
  // Keep the native app scale stable when the website updates its page head.
  new MutationObserver(apply).observe(head, {
    childList: true, subtree: true, attributes: true, attributeFilter: ['name', 'content']
  });
  const style = document.createElement('style');
  const nonceSource = document.querySelector('style[nonce],script[nonce]');
  if (nonceSource) style.nonce = nonceSource.nonce;
  style.textContent = 'html { touch-action: pan-x pan-y; } input:not([type="checkbox"]):not([type="radio"]),select,textarea { font-size: max(16px, 1em) !important; }';
  head.appendChild(style);
})();

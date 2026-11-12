const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const handlers = {};
const frame = { style: { setProperty() {} }, addEventListener(n, fn) { handlers[n] = fn; } };
const img = { style: {}, clientWidth: 300, clientHeight: 200, closest: () => frame, addEventListener(n, fn) { this[n] = fn; } };
vm.runInNewContext(readFileSync(__dirname + '/../ios/App/App/MobileAssets/photo-zoom.js', 'utf8'), {
 document: { documentElement: {}, querySelectorAll(selector) {
  assert.equal(selector, '.photo-full-view img.photo-full-image'); return [img];
 } }, MutationObserver: class { observe() {} }
});
const event = touches => ({ target: img, touches: touches.map(([clientX,clientY]) => ({clientX,clientY})), cancelable: true, preventDefault() {} });
handlers.touchstart(event([[0,0],[100,0]]));
handlers.touchmove(event([[0,0],[250,0]]));
assert.match(img.style.transform, /scale\(2.5\)/);
handlers.touchend(event([[100,100]]));
handlers.touchmove(event([[130,120]]));
assert.match(img.style.transform, /translate\(30px,20px\)/);
img.load();
assert.match(img.style.transform, /translate\(0px,0px\) scale\(1\)/);
handlers.touchstart({ target: {}, touches: [] });
handlers.touchmove(event([[0,0],[300,0]]));
assert.match(img.style.transform, /scale\(1\)/);
console.log('PASS: full-view-only selector, pinch, pan, image change reset, controls excluded');

const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(__dirname + '/../android/app/src/main/assets/pull-refresh.js', 'utf8');
function fixture() {
  let refreshes = 0;
  const handlers = {};
  const document = {
    scrollingElement: { scrollTop: 0 },
    documentElement: { appendChild() {} },
    createElement() { return { setAttribute() {}, style: {}, remove() {} }; },
    addEventListener(name, cb) { handlers[name] = cb; }
  };
  const window = { scrollY: 0, PhotoRefresh: { postMessage(value) { assert.equal(value, 'refresh'); refreshes++; } } };
  vm.runInNewContext(source, { window, document });
  const node = (scrollTop=0,parentElement=null,editable=false) => ({ nodeType:1,scrollTop,parentElement,matches:()=>editable });
  const fire = (name, target, x=0, y=0, count=1, cancelable=true) => {
    let prevented=false;
    handlers[name]({target,touches:Array.from({length:count},()=>({clientX:x,clientY:y})),cancelable,preventDefault(){prevented=true;}});
    return prevented;
  };
  return {window,document,node,fire,get count(){return refreshes;}};
}
let f=fixture(), target=f.node(0,f.node(400));
f.fire('touchstart',target);assert.equal(f.fire('touchmove',target,0,150),false);f.fire('touchend',target);assert.equal(f.count,0,'Nested scroll must not refresh or consume the swipe');
f=fixture();target=f.node();f.document.scrollingElement.scrollTop=300;f.fire('touchstart',target);assert.equal(f.fire('touchmove',target,0,150),false);f.fire('touchend',target);assert.equal(f.count,0);
f=fixture();target=f.node();f.fire('touchstart',target);assert.equal(f.fire('touchmove',target,0,110),true);f.fire('touchend',target);assert.equal(f.count,1);
f.fire('touchstart',target);f.fire('touchmove',target,0,110);f.fire('touchend',target);assert.equal(f.count,1,'Busy refresh must not duplicate');
f.window.__photoPullRefresh.reset();f.fire('touchstart',target);f.fire('touchmove',target,0,110);f.fire('touchend',target);assert.equal(f.count,2,'Completion/timeout reset permits next refresh');
for(const [dx,dy,count,cancelable] of [[0,-150,1,true],[150,20,1,true],[0,50,1,true],[0,150,2,true],[0,150,1,false]]) {
 f=fixture();target=f.node();f.fire('touchstart',target);f.fire('touchmove',target,dx,dy,count,cancelable);f.fire('touchend',target);assert.equal(f.count,0);
}
f=fixture();target=f.node(0,null,true);f.fire('touchstart',target);assert.equal(f.fire('touchmove',target,0,150),false);f.fire('touchend',target);assert.equal(f.count,0);
f=fixture();target=f.node();f.fire('touchstart',target);f.fire('touchmove',target,0,150);f.fire('touchcancel',target);f.fire('touchend',target);assert.equal(f.count,0);
console.log('PASS: nested/document scroll, top pull, duplicate prevention, reset, upward/horizontal/short/multitouch/noncancelable gestures, form controls, cancellation');

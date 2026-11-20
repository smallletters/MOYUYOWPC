const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await (await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:2,isMobile:true,hasTouch:true})).newPage();
  const r = await p.goto('http://192.168.64.103:5174/', {waitUntil:'networkidle', timeout:30000});
  await p.waitForTimeout(2500);
  console.log('FINAL URL:', p.url());
  await p.screenshot({path:'tools/shots/root.png'});
  await b.close();
})();

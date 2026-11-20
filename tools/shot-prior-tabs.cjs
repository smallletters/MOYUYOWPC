const { chromium } = require('playwright');
const path = require('path');
const OUT = path.resolve(__dirname,'shots');
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({viewport:{width:1440,height:960},deviceScaleFactor:1});
  const p = await ctx.newPage();
  await p.goto('file:///' + path.resolve(__dirname,'..','deliverables/ui-prototype/index.html').replace(/\\/g,'/'), {waitUntil:'networkidle',timeout:20000});
  await p.waitForTimeout(2000);
  for (const label of ['商城','社区','消息','我的']) {
    const el = p.locator(`text="${label}"`).last();
    try { await el.click({timeout:4000}); await p.waitForTimeout(1200);
      await p.screenshot({path:`${OUT}/prior-${label}.png`});
      console.log('OK',label);
    } catch(e){ console.log('FAIL',label, e.message.split('\n')[0]); }
  }
  await b.close();
})();

const { chromium } = require('playwright');
const path = require('path');
const OUT = path.resolve(__dirname,'shots');
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({viewport:{width:1440,height:960},deviceScaleFactor:1});
  const p = await ctx.newPage();
  for (const [tag, file] of [['prior-redesign','deliverables/moyuyo-redesign/index.html'],['prior-ui','deliverables/ui-prototype/index.html']]) {
    try {
      await p.goto('file:///' + path.resolve(__dirname,'..',file).replace(/\\/g,'/'), {waitUntil:'networkidle',timeout:20000});
      await p.waitForTimeout(2500);
      await p.screenshot({path:`${OUT}/${tag}.png`});
      console.log('OK',tag);
    } catch(e){ console.log('FAIL',tag,e.message); }
  }
  await b.close();
})();

const { chromium } = require('playwright');
const fs=require('fs'), path=require('path');
const OUT = path.resolve(__dirname,'final-shots');
const TABS=[['pet','宠物'],['home','商城'],['community','社区'],['messages','消息'],['user','我的']];
(async()=>{
  fs.mkdirSync(OUT,{recursive:true});
  const b=await chromium.launch();
  const ctx=await b.newContext({viewport:{width:1440,height:1040},deviceScaleFactor:2});
  const p=await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message)); p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
  await p.goto('file:///D:/MOYUYOWPC/deliverables/moyuyo-ios-redesign/prototype/index.html',{waitUntil:'networkidle'});
  await p.waitForTimeout(2500);
  await p.screenshot({path:path.join(OUT,'00-review-panel.png')});
  const dev=p.locator('.device');
  for(const [slug,label] of TABS){
    try{
      await p.locator(`.tabbar__item[data-tab="${slug}"]`).first().click({timeout:4000});
      await p.waitForTimeout(1400);
      await dev.screenshot({path:path.join(OUT,`${slug}.png`)});
      console.log('OK',slug);
    }catch(e){ console.log('FAIL',slug,e.message.split('\n')[0]); }
  }
  console.log('errors:',errs.length, errs.slice(0,5));
  await b.close();
})();

const { chromium } = require('D:/MOYUYOWPC/node_modules/playwright');
const path = require('path');
const SHOTS = 'D:/MOYUYOWPC/.workbuddy/design/prototype/_shots';
const PROTO = 'file:///D:/MOYUYOWPC/.workbuddy/design/prototype/index.html';
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport:{width:1500,height:1000}, deviceScaleFactor:2 });
  const p = await ctx.newPage();
  await p.goto(PROTO, { waitUntil:'load' });
  await p.waitForTimeout(700);
  const clipPhone = async () => { const b = await p.locator('#phone').boundingBox(); return {x:b.x-14,y:b.y-14,width:b.width+28,height:b.height+28}; };
  const shot = async (n) => p.screenshot({ path: path.join(SHOTS, n), clip: await clipPhone() });

  // messages 通知段（浅 + 深）
  await p.click('#pbTabs button[data-page="messages"]'); await p.waitForTimeout(300);
  await p.selectOption('#pbState','normal'); await p.waitForTimeout(250);
  await p.click('#page-messages .msg-seg-tab[data-i="1"]'); await p.waitForTimeout(400);
  await shot('tab-messages-notif-light.png');
  await p.click('#pbTheme button[data-theme="dark"]'); await p.waitForTimeout(400);
  await shot('tab-messages-notif-dark.png');
  await p.click('#pbTheme button[data-theme="light"]'); await p.waitForTimeout(300);
  await p.click('#page-messages .msg-seg-tab[data-i="0"]'); await p.waitForTimeout(300);

  // community：推荐 tab（发现入口 rail）/ 话题 tab 默认态（选择器网格）/ 话题 tab 选中态（收起为已选 chip）
  await p.click('#pbTabs button[data-page="community"]'); await p.waitForTimeout(250);
  await p.selectOption('#pbState','normal'); await p.waitForTimeout(250);
  await shot('tab-community-recommend-light.png');
  await p.click('#page-community .issue-tab[data-i="2"]'); await p.waitForTimeout(400);
  await shot('tab-community-topic-default-light.png');
  await p.click('#page-community .topic-grid .topic-chip[data-i="3"]'); await p.waitForTimeout(400);
  await shot('tab-community-topic-selected-light.png');

  // user 未登录（会员入口暗示）
  await p.click('#pbTabs button[data-page="user"]'); await p.waitForTimeout(250);
  await p.selectOption('#pbState','login'); await p.waitForTimeout(400);
  await shot('tab-user-login-light.png');
  await p.selectOption('#pbState','normal'); await p.waitForTimeout(300);

  // 会员卡特写
  const card = await p.locator('#page-user .vip-card').boundingBox();
  await p.screenshot({ path: path.join(SHOTS,'detail-vip-card-light.png'), clip:{x:card.x-8,y:card.y-8,width:card.width+16,height:card.height+16} });
  await p.click('#pbTheme button[data-theme="dark"]'); await p.waitForTimeout(400);
  const card2 = await p.locator('#page-user .vip-card').boundingBox();
  await p.screenshot({ path: path.join(SHOTS,'detail-vip-card-dark.png'), clip:{x:card2.x-8,y:card2.y-8,width:card2.width+16,height:card2.height+16} });
  await p.click('#pbTheme button[data-theme="light"]'); await p.waitForTimeout(300);

  // pet 空态
  await p.click('#pbTabs button[data-page="pet"]'); await p.waitForTimeout(250);
  await p.selectOption('#pbState','empty'); await p.waitForTimeout(400);
  await shot('tab-pet-empty-light.png');

  console.log('extra shots done, total:', require('fs').readdirSync(SHOTS).length);
  await b.close();
})();

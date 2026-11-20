const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const SRC = path.resolve(__dirname, '..', 'design', 'assets');
const OUT = path.resolve(__dirname, '..', 'design', 'assets-opt');

const JOBS = [
  ['hero-banner.png', 1200, 800, 0.85],
  ['hero-pet.png', 900, 1200, 0.85],
  ['prod-grooming.png', 700, 700, 0.84],
  ['prod-carrier.png', 700, 700, 0.84],
  ['prod-bowls.png', 700, 700, 0.84],
  ['prod-plush.png', 700, 700, 0.84],
  ['prod-harness.png', 700, 700, 0.84],
  ['prod-cattower.png', 700, 700, 0.84],
  ['prod-fountain.png', 700, 700, 0.84],
  ['prod-bed.png', 700, 700, 0.84],
  ['prod-treats.png', 700, 700, 0.84],
  ['prod-ball.png', 700, 700, 0.84],
  ['prod-blanket.png', 700, 700, 0.84],
  ['prod-collar.png', 700, 700, 0.84],
  ['post-dog.png', 800, 1200, 0.84],
  ['post-cat.png', 800, 800, 0.84],
  ['post-flatlay.png', 800, 800, 0.84],
  ['post-bath.png', 800, 800, 0.84],
  ['post-run.png', 800, 800, 0.84],
];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('about:blank');
  for (const [file, w, h, q] of JOBS) {
    const src = path.join(SRC, file);
    if (!fs.existsSync(src)) { console.log('MISS', file); continue; }
    const b64 = fs.readFileSync(src).toString('base64');
    const dataUrl = await page.evaluate(async ({ b64, w, h, q }) => {
      const img = new Image();
      img.src = 'data:image/png;base64,' + b64;
      await img.decode();
      const c = document.createElement('canvas');
      c.width = w; c.height = h;
      const ctx = c.getContext('2d');
      ctx.imageSmoothingQuality = 'high';
      // 裁掉底部 9% —— 去除生成模型打在右下角的 "AI生成 WORKBUDDY" 水印
      const cw = img.width;
      const ch = Math.round(img.height * 0.91);
      // cover-crop to exact target aspect
      const sr = cw / ch, dr = w / h;
      let sw = cw, sh = ch, sx = 0, sy = 0;
      if (sr > dr) { sw = ch * dr; sx = (cw - sw) / 2; }
      else { sh = cw / dr; sy = (ch - sh) / 2; }
      ctx.drawImage(img, sx, sy, sw, sh, 0, 0, w, h);
      return c.toDataURL('image/jpeg', q);
    }, { b64, w, h, q });
    const buf = Buffer.from(dataUrl.split(',')[1], 'base64');
    fs.writeFileSync(path.join(OUT, file.replace('.png', '.jpg')), buf);
    console.log('OK', file, '->', (buf.length / 1024).toFixed(0) + 'KB');
  }
  await browser.close();
})();

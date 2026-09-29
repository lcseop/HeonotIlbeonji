const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
  });
  const htmlPath = path.resolve(__dirname, 'banner.html');
  const url = `file:///${htmlPath.replace(/\\/g, '/')}`;

  const preview = await browser.newPage({ viewport: { width: 5460, height: 1200 }, deviceScaleFactor: 1 });
  await preview.goto(url, { waitUntil: 'networkidle' });
  await preview.screenshot({ path: path.resolve(__dirname, 'heonot-ilbeonji-truck-banner-273x60.png'), fullPage: false });

  const print = await browser.newPage({ viewport: { width: 5460, height: 1200 }, deviceScaleFactor: 2 });
  await print.goto(url, { waitUntil: 'networkidle' });
  await print.screenshot({ path: path.resolve(__dirname, 'heonot-ilbeonji-truck-banner-273x60-print-100dpi.png'), fullPage: false });
  await browser.close();
})();

const fs=require('fs');
const path=require('path');
const {chromium}=require('playwright');
(async()=>{
 const src=fs.readFileSync(path.join(__dirname,'truck-banner-bold-illustrated-concept.png')).toString('base64');
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="273cm" height="60cm" viewBox="0 0 5460 1200"><defs><clipPath id="left"><rect width="718" height="1200"/></clipPath><clipPath id="right"><rect x="718" width="4742" height="1200"/></clipPath></defs><image href="data:image/png;base64,${src}" width="3481" height="1200" preserveAspectRatio="none" clip-path="url(#left)"/><image href="data:image/png;base64,${src}" x="-513.2" width="5973.2" height="1200" preserveAspectRatio="none" clip-path="url(#right)"/></svg>`;
 fs.writeFileSync(path.join(__dirname,'truck-banner-final-273x60.svg'),svg);
 const browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'});
 const page=await browser.newPage({viewport:{width:5460,height:1200},deviceScaleFactor:2});
 await page.setContent(`<style>html,body{margin:0}svg{display:block;width:5460px;height:1200px}</style>${svg}`);
 await page.locator('image').first().evaluate(async()=>{await new Promise(r=>setTimeout(r,500))});
 await page.screenshot({path:path.join(__dirname,'truck-banner-final-273x60.png')});
 await page.setViewportSize({width:1820,height:400});
 await page.addStyleTag({content:'svg{width:1820px!important;height:400px!important}'});
 await page.screenshot({path:path.join(__dirname,'truck-banner-final-preview.png')});
 await browser.close();
})();

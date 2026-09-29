const sharp=require('sharp');
const path=require('path');
(async()=>{
 const dir=__dirname;
 const source='C:/Users/소금/Desktop/작업물/광고/헌옷일번지 광고판 (비율 수정버전).png';
 const edit='C:/Users/소금/.codex/generated_images/01a05750-34ab-7523-8b27-ebccfffa01fc/exec-71bfa301-f550-44c5-9ed9-df3e583523a3.png';
 const resized=await sharp(edit).resize(2475,2500,{fit:'fill'}).png().toBuffer();
 // Mask only the repaired anatomical-right arm and the adjacent carton edge.
 const maskSvg=`<svg width="2475" height="2500" xmlns="http://www.w3.org/2000/svg"><path d="M 215 1490 L 420 1490 L 505 1580 L 465 1760 L 455 1910 L 475 2090 L 420 2280 L 330 2360 L 155 2340 L 150 2110 L 180 1850 Z" fill="white"/></svg>`;
 const mask=await sharp(Buffer.from(maskSvg)).blur(7).png().toBuffer();
 const patch=await sharp(resized).ensureAlpha().composite([{input:mask,blend:'dest-in'}]).png().toBuffer();
 const output=path.join(dir,'truck-banner-arm-fixed-11375x2500-v6.png');
 await sharp(source).composite([{input:patch,left:8900,top:0}]).png().toFile(output);
 await sharp(output).resize(2730,600).png().toFile(path.join(dir,'truck-banner-arm-fixed-v6-preview.png'));
 await sharp(output).extract({left:8900,top:0,width:2475,height:2500}).resize(990,1000).png().toFile(path.join(dir,'character-arm-fixed-v6-preview.png'));
 const m=await sharp(output).metadata();
 console.log(JSON.stringify({output,width:m.width,height:m.height}));
})();

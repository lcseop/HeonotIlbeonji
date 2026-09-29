const sharp = require('sharp');
const path = require('path');
const source = 'C:/Users/소금/.codex/generated_images/01a05750-34ab-7523-8b27-ebccfffa01fc/exec-28d0c047-1d24-4816-b77d-06e8bdb35270.png';
(async () => {
  const m = await sharp(source).metadata();
  const width = 10920, height = 2400;
  const split = Math.round(m.width * 0.75);
  const rightWidth = Math.round((m.width - split) * height / m.height);
  const leftWidth = width - rightWidth;
  const left = await sharp(source).extract({left:0,top:0,width:split,height:m.height}).resize(leftWidth,height,{fit:'fill'}).toBuffer();
  const right = await sharp(source).extract({left:split,top:0,width:m.width-split,height:m.height}).resize(rightWidth,height,{fit:'fill'}).toBuffer();
  const output = path.join(__dirname, 'truck-banner-heonot-273x60-v4.png');
  await sharp({create:{width,height,channels:3,background:'#ffdc00'}}).composite([{input:left,left:0,top:0},{input:right,left:leftWidth,top:0}]).withMetadata({density:101.6}).png().toFile(output);
  await sharp(output).resize(2730,600).png().toFile(path.join(__dirname,'truck-banner-heonot-273x60-v4-preview.png'));
  const verified = await sharp(output).metadata();
  console.log(JSON.stringify({output,width:verified.width,height:verified.height,ratio:verified.width/verified.height,dpi:verified.density,physicalCm:[verified.width/verified.density*2.54,verified.height/verified.density*2.54]}));
})();

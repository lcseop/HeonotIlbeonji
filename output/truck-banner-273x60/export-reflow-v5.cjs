const sharp = require('sharp');
const path = require('path');
const source = 'C:/Users/소금/.codex/generated_images/01a05750-34ab-7523-8b27-ebccfffa01fc/exec-7a354d41-7e91-4999-b963-9fef8ff52ee0.png';
(async () => {
  const {data, info} = await sharp(source).removeAlpha().raw().toBuffer({resolveWithObject:true});
  const rows=[];
  for(let y=0;y<info.height;y++){
    let magenta=0;
    for(let x=0;x<info.width;x++){
      const i=(y*info.width+x)*info.channels;
      if(data[i]>190 && data[i+1]<80 && data[i+2]>190) magenta++;
    }
    if(magenta/info.width<0.005) rows.push(y);
  }
  const top=rows[0]+1, bottom=rows[rows.length-1]-1;
  const crop={left:0,top,width:info.width,height:bottom-top+1};
  const output=path.join(__dirname,'truck-banner-reflow-11375x2500-v5.png');
  await sharp(source).extract(crop).resize(11375,2500,{fit:'contain',position:'centre',background:'#001b45'}).png().toFile(output);
  await sharp(output).resize(2730,600).png().toFile(path.join(__dirname,'truck-banner-reflow-v5-preview.png'));
  const m=await sharp(output).metadata();
  console.log(JSON.stringify({crop,output,width:m.width,height:m.height,ratio:m.width/m.height,resize:'uniform scale with thin navy top/bottom finishing border; no crop or nonuniform stretching'}));
})();

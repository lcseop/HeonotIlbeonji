const sharp=require('sharp');
const path=require('path');
(async()=>{
 const source='C:/Users/소금/Desktop/작업물/광고/헌옷일번지 광고판 (비율 수정버전).png';
 console.log(await sharp(source).metadata());
 await sharp(source).extract({left:8900,top:0,width:2475,height:2500}).resize(1485,1500).png().toFile(path.join(__dirname,'character-arm-edit-input-v6.png'));
})();

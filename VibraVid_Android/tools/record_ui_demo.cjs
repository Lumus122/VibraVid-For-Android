'use strict';
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),M=require(root+'/app/src/main/assets/model.js');
const out=process.env.VIBRAVID_UI_OUTPUT||path.join(root,'build/streaming');
const browse=JSON.parse(fs.readFileSync(root+'/app/src/main/assets/discovery.json','utf8'));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 let context;
 try{
  const tmp=path.join(root,'build/demo-capture');fs.mkdirSync(tmp,{recursive:true});fs.mkdirSync(out,{recursive:true});
  context=await browser.newContext({viewport:{width:390,height:844},recordVideo:{dir:tmp,size:{width:390,height:844}}});
  let data=M.empty();for(const row of [browse.movies[1],browse.series[0],browse.series[1]])data=M.add(data,row).data;
  await context.addInitScript(value=>localStorage.setItem('vv.android.preview',JSON.stringify(value)),data);
  const page=await context.newPage(),video=page.video();
  await page.goto(process.env.VIBRAVID_PREVIEW_URL||'http://127.0.0.1:38912/');await page.waitForFunction(()=>VibraApp?.diagnostics().ready&&document.querySelectorAll('.home-open').length>10);await page.evaluate(()=>document.fonts.ready);
  await page.waitForTimeout(1000);
  await page.locator('[data-rail="movie-1"]').evaluate(e=>e.scrollBy({left:285,behavior:'smooth'}));await page.waitForTimeout(850);
  await page.locator('[data-rail="movie-1"]').evaluate(e=>e.scrollTo({left:0,behavior:'smooth'}));await page.waitForTimeout(700);
  await page.locator('.home-open').first().click();await page.waitForTimeout(1300);
  await page.locator('[data-action="close-sheet"]').click();await page.waitForTimeout(650);
  await page.locator('[data-rail="series-0"] .home-open').first().click();await page.waitForTimeout(1100);
  await page.locator('[data-action="close-sheet"]').click();await page.waitForTimeout(600);
  await page.locator('.bottom-nav [data-page="library"]').click();await page.waitForTimeout(1200);
  await page.locator('#open-settings').click();await page.waitForTimeout(800);await page.locator('[data-action="settings-tab"][data-value="appearance"]').click();await page.waitForTimeout(900);
  await page.locator('.bottom-nav [data-page="home"]').click();await page.waitForTimeout(1000);
  await context.close();context=null;await video.saveAs(path.join(out,'09-transizioni.webm'));console.log('UI motion demo saved. Browser preview, illustrative library, no phone performance claim.');
 }finally{if(context)await context.close();await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});

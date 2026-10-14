'use strict';
// Run with NODE_PATH pointing to an installed Playwright package. Uses an isolated temporary profile.
const {chromium}=require('playwright'),assert=require('assert/strict'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),M=require(path.join(root,'app/src/main/assets/model.js'));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 try{
 const context=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 let data=M.add(M.empty(),{name:'Film',kind:'movie',notes:'Salva video <script> & "映画" 😀'}).data;
 data=M.add(data,{name:'Lingua',kind:'series',notes:'العربية'}).data;
 await context.addInitScript(value=>{if(!localStorage.getItem('vv.android.preview'))localStorage.setItem('vv.android.preview',JSON.stringify(value));},data);
 await page.goto(process.env.VIBRAVID_PREVIEW_URL||'http://127.0.0.1:38912/#settings');
 await page.waitForFunction(()=>window.VibraApp?.diagnostics().ready);
 assert.equal(await page.locator('#app-language option').count(),21);
 const languages=await page.evaluate(()=>window.VibraI18n.languages);let checks=0;
 for(const language of languages){
  await page.locator('#app-language').selectOption(language.code);
  await page.waitForFunction(code=>window.VibraApp.diagnostics().language===code,language.code);
  assert.equal(await page.locator('html').getAttribute('lang'),language.code);
  assert.equal(await page.locator('html').getAttribute('dir'),language.code==='ar'?'rtl':'ltr');
  assert.equal(await page.locator('#app-language').inputValue(),language.code);
  assert.equal(await page.locator('[data-action="app-update"]').textContent(),'Update');
  const expected=await page.evaluate(()=>window.VibraI18n.text('Libreria'));
  assert.equal(await page.locator('[data-page="library"] span').textContent(),expected);
  const fits=await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2);assert.ok(fits,'Settings overflow '+language.code);
  await page.locator('[data-page="library"]').click();await page.waitForFunction(()=>window.VibraApp.diagnostics().page==='library');
  assert.deepEqual(await page.locator('.card-title').allTextContents(),['Lingua','Film']);
  assert.equal(await page.locator('.library-card').count(),2);
  await page.locator('.card-open').filter({hasText:'Film'}).click();
  assert.equal(await page.locator('textarea[name="notes"]').inputValue(),'Salva video <script> & "映画" 😀');
  await page.locator('[data-action="close-sheet"]').click();
  await page.locator('#open-settings').click();await page.waitForFunction(()=>window.VibraApp.diagnostics().page==='settings');
  checks+=9;
 }
 // Light palette, compact phone, long translations, and right-to-left setting must fit.
 await page.setViewportSize({width:320,height:720});
 for(const code of ['de','ar','ja','bn']){
  await page.locator('#app-language').selectOption(code);await page.waitForFunction(value=>window.VibraApp.diagnostics().language===value,code);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),'320px overflow '+code);checks++;
 }
 await page.locator('#app-language').selectOption('en');await page.waitForFunction(()=>window.VibraApp.diagnostics().language==='en');
 await page.locator('[data-action="appearance"][data-key="palette"][data-value="paper"]').click().catch(()=>{});
 await page.screenshot({path:path.join(root,'build/i18n/settings-en.png'),fullPage:true});
 // Reload without the seed script to prove persisted preferences.
 await context.clearCookies();await page.evaluate(()=>location.reload());await page.waitForFunction(()=>window.VibraApp?.diagnostics().ready);
 assert.equal(await page.locator('#app-language').inputValue(),'en');checks++;
 assert.deepEqual(errors,[]);checks++;
 const result={passed:checks,languages:languages.length,pageErrors:errors};fs.writeFileSync(path.join(root,'build/i18n/ui-results.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});

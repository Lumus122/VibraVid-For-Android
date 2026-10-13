(function () {
  'use strict';
  // Specific ad containers only: never hide generic iframes, videos or players.
  const id = 'vibravid-adblock-style';
  if (document.getElementById(id)) return;
  const style = document.createElement('style');
  style.id = id;
  const selector = '.adsbygoogle,[data-ad-slot],[data-ad-client],'
    + '[id^="google_ads_iframe"],[id^="div-gpt-ad"],'
    + '.ad-banner,.ad-container,.advertisement,.advertisement-container,'
    + 'iframe[src*="doubleclick.net/"],iframe[src*="googlesyndication.com/"]';
  style.textContent = selector + '{display:none!important;visibility:hidden!important;pointer-events:none!important}';
  (document.head || document.documentElement).appendChild(style);
  // CSSOM also works on pages that forbid inline stylesheet text with a CSP.
  function hide(root) {
    if (root.nodeType !== 1) return;
    const elements = Array.from(root.querySelectorAll(selector));
    if (root.matches(selector)) elements.push(root);
    elements.forEach(function (element) { element.style.setProperty('display', 'none', 'important'); });
  }
  hide(document.documentElement);
  new MutationObserver(function (records) {
    records.forEach(function (record) { if(record.type==='attributes')hide(record.target);else record.addedNodes.forEach(hide); });
  }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class','id','src','data-ad-slot','data-ad-client'] });
})();

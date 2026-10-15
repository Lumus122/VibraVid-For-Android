'use strict';
// Shared artwork uses transform/opacity only and never modifies title or library data.
window.VibraMotion=(()=>{
  let sequence=0,flight=null;
  const reduced=()=>document.documentElement.dataset.reduced==='true'||matchMedia('(prefers-reduced-motion: reduce)').matches;
  function stop(){sequence++;if(flight){flight.animation?.cancel();flight.element.remove();flight.destination.style.opacity=flight.opacity;flight=null;}}
  function run(anchor,destination,reverse){
    stop();
    const source=anchor?.querySelector('.poster img'),sheet=document.querySelector('#sheet');
    if(reduced()||!source||!destination||!source.isConnected||!sheet.open)return Promise.resolve();
    const from=source.getBoundingClientRect(),to=destination.getBoundingClientRect();
    if(from.width<1||to.width<1||from.bottom<0||from.top>innerHeight)return Promise.resolve();
    const clone=source.cloneNode();clone.removeAttribute('loading');clone.alt='';clone.setAttribute('aria-hidden','true');clone.className='shared-artwork';
    Object.assign(clone.style,{position:'fixed',left:to.left+'px',top:to.top+'px',width:to.width+'px',height:to.height+'px',objectFit:'cover',objectPosition:'center',transformOrigin:'0 0',zIndex:'50',pointerEvents:'none',margin:'0'});
    sheet.append(clone);const opacity=destination.style.opacity;destination.style.opacity='0';
    const token=sequence,small=`translate(${from.left-to.left}px,${from.top-to.top}px) scale(${from.width/to.width},${from.height/to.height})`;
    const frames=reverse?[{transform:'none',opacity:1,borderRadius:'0px'},{transform:small,opacity:0,borderRadius:'5px'}]:[{transform:small,opacity:1,borderRadius:'5px'},{transform:'none',opacity:1,borderRadius:'0px'}];
    const animation=clone.animate(frames,{duration:reverse?280:400,easing:'cubic-bezier(.2,.75,.2,1)',fill:'both'});flight={element:clone,animation,destination,opacity};
    return animation.finished.catch(()=>{}).then(()=>{if(sequence===token){clone.remove();destination.style.opacity=opacity;flight=null;}});
  }
  function expand(anchor,destination){
    const sheet=document.querySelector('#sheet');if(sheet.dataset.expanded==='true')return;
    sheet.dataset.expanded='true';if(reduced())return;
    sheet.getAnimations().forEach(animation=>animation.cancel());
    sheet.querySelector('.hero-copy')?.animate([{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'none'}],{duration:280,delay:100,easing:'cubic-bezier(.2,.75,.2,1)'});
    run(anchor,destination,false);
  }
  return {expand,collapse:(anchor,destination)=>run(anchor,destination,true),stop,get active(){return !!flight;}};
})();

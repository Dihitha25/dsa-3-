const $=s=>document.querySelector(s),e=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])),enc=encodeURIComponent;
const api=u=>fetch('/api/'+u).then(r=>r.json());
let saved=new Set(),viewed=new Set(),shown=0,qs='';
const NAV=[['home','Home'],['search','Search'],['recs','Recommendations'],['net','Authors & Network'],['ana','Analytics'],['sav','Reading List']];
$('#nav').innerHTML=NAV.map(n=>`<button data-g="${n[0]}">${n[1]}</button>`).join('');
$('#nav').onclick=ev=>{if(ev.target.dataset.g)go(ev.target.dataset.g)};
function go(id,q){$('#hs').style.display='none';document.querySelectorAll('section').forEach(s=>s.classList.toggle('on',s.id===id));document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.g===id));scrollTo(0,0);
if(q!==undefined){$('#q').value=q;run()}if(id==='recs')recs();if(id==='sav')savedView();if(id==='net')authors();if(id==='ana'&&!$('#an').innerHTML)analytics()}
const card=(p,x='')=>`<div class="card p"><h3>${e(p.t)}</h3><div>${p.a.map(a=>`<a class="au" data-au="${e(a)}">${e(a)}</a>`).join('')}</div>
<div class="meta"><span class="chip">${e(p.c)}</span><span class="chip">${p.y}</span><span class="chip">${e(p.id)}</span>${x}</div><div class="mu">${e(p.s)}${p.s.length>=400?'…':''}</div>
<div style="margin-top:8px"><button class="btn s" data-sv="${e(p.id)}">${saved.has(p.id)?'★ Saved':'☆ Save'}</button> <button class="btn s" data-sim="${e(p.id)}">Similar papers</button></div></div>`;
/* search (all work is done by Java; the page only asks for 15 results at a time) */
async function run(){const q=$('#q').value.trim(),cat=$('#cat').value;
if(!q&&!cat){$('#info').innerHTML='<div class="note">Type a keyword or pick a domain to search.</div>';$('#res').innerHTML='';return}
qs=`q=${enc(q)}&field=${$('#fld').value}&cat=${enc(cat)}&alg=${$('#alg').value}&sort=${$('#srt').value}`;shown=0;$('#res').innerHTML='';$('#info').innerHTML='<div class="mu">Searching…</div>';await more(true);hist()}
async function more(first){$('#more')?.remove();const r=await api(`search?${qs}&offset=${shown}`);
if(first){let i=`<div class="mu" style="margin-bottom:10px">${r.total.toLocaleString()} papers · ${$('#alg').value.toUpperCase()} · ${r.ms} ms</div>`;
if(!r.total&&r.did)i+=`<div class="note">No matches. Did you mean <a class="au" data-h="${e(r.did)}"><b>${e(r.did)}</b></a>? You can also try Fuzzy search.</div>`;$('#info').innerHTML=i}
r.res.forEach(p=>$('#res').insertAdjacentHTML('beforeend',card(p)));shown+=r.res.length;
if(shown<r.total)$('#res').insertAdjacentHTML('beforeend','<button class="btn" id="more" onclick="more()">Show more</button>')}
const hist=()=>api('history').then(h=>$('#hist').innerHTML=h.length?'Recent: '+h.map(x=>`<a class="au" data-h="${e(x)}">${e(x)}</a>`).join(''):'');
$('#go').onclick=run;$('#q').onkeydown=ev=>{if(ev.key==='Enter')run()};$('#hq').onkeydown=ev=>{if(ev.key==='Enter')go('search',$('#hq').value)};
/* suggestions: dropdown under the home box, datalist on the search page */
async function sug(inp,box,dl){const v=inp.value.trim();if(v.length<2){if(box)box.style.display='none';return}const l=await api('suggest?q='+enc(v));
if(dl)dl.innerHTML=l.filter(x=>x.k==='keyword').map(x=>`<option value="${e(x.v)}">`).join('');
if(box){box.innerHTML=l.map(x=>`<div data-sg="${e(x.v)}"><span>${e(x.v)}</span><em>${x.k}</em></div>`).join('');box.style.display=l.length?'block':'none'}}
$('#hq').oninput=()=>sug($('#hq'),$('#hs'));$('#q').oninput=()=>sug($('#q'),null,$('#sg'));
/* recommendations */
async function recs(){const ids=[...new Set([...saved,...viewed])].join(',');
$('#rl').innerHTML=ids?(await api('recs?ids='+enc(ids))).map(p=>card(p,`<span class="chip">match ${p.m}%</span>`)).join(''):'<div class="card mu">Nothing yet. Save papers or open "Similar papers" from the search page.</div>'}
/* authors & network */
async function authors(){const l=await api('authors?q='+enc($('#aq').value));
$('#al').innerHTML=l.map(a=>`<div class="card" style="margin-bottom:8px;cursor:pointer" data-au="${e(a.n)}"><b>${e(a.n)}</b><div class="meta">${a.p} papers · ${a.c} collaborators · influence ${a.p+a.c}</div></div>`).join('')}
$('#aq').oninput=authors;
async function author(n){go('net');const a=await api('author?name='+enc(n));if(!a.papers)return;
$('#ad').innerHTML=`<h3>${e(n)}</h3><div class="meta">${a.papers.length} papers · ${a.c} collaborators · influence score ${a.papers.length+a.c}</div><b>Collaborators</b><div style="margin:6px 0 12px">${a.collabs.map(c=>`<a class="au" data-au="${e(c[0])}">${e(c[0])} (${c[1]})</a>`).join('')||'<span class="mu">None in this dataset</span>'}</div><b>Papers</b>${a.papers.map(p=>`<div style="margin:6px 0">• ${e(p.t)} <span class="mu">(${p.y})</span></div>`).join('')}`}
/* analytics */
const bars=(t,en)=>{const mx=Math.max(...en.map(x=>x[1]));return `<div class="card"><h3>${t}</h3>${en.map(x=>`<div class="row"><span title="${e(x[0])}">${e(x[0])}</span><i style="width:${x[1]/mx*100}%"></i><span>${x[1]}</span></div>`).join('')}</div>`};
async function analytics(){const a=await api('analytics');$('#an').innerHTML=bars('Papers by domain',a.d)+bars('Top authors (influence)',a.a)+bars('Papers per year',a.y)+bars('Trending keywords',a.k)}
/* reading list (stored by Java in data/saved.txt) */
async function savedView(){const l=await api('saved');$('#sl').innerHTML=l.map(p=>card(p)).join('')||'<div class="card mu">No saved papers yet.</div>';
$('#csv').value='id,title,category,year\n'+l.map(p=>[p.id,'"'+p.t.replace(/"/g,'""')+'"',p.c,p.y].join(',')).join('\n')}
document.addEventListener('click',async ev=>{const t=ev.target.closest('[data-sg]'),d=(t||ev.target).dataset;
if(!ev.target.closest('#hs')&&ev.target.id!=='hq')$('#hs').style.display='none';
if(d.sg!==undefined)go('search',d.sg);if(d.au)author(d.au);if(d.h!==undefined)go('search',d.h);
if(d.sv!==undefined){const r=await api('save?id='+enc(d.sv));r.saved?saved.add(d.sv):saved.delete(d.sv);ev.target.textContent=r.saved?'★ Saved':'☆ Save';if($('#sav').classList.contains('on'))savedView()}
if(d.sim!==undefined){viewed.add(d.sim);const c=ev.target.closest('.card');c.nextElementSibling?.classList.contains('simx')&&c.nextElementSibling.remove();
const l=await api('recs?k=4&ids='+enc(d.sim));c.insertAdjacentHTML('afterend',`<div class="simx" style="margin-left:22px">${l.map(p=>card(p,`<span class="chip">match ${p.m}%</span>`)).join('')}</div>`)}});
/* start-up */
api('meta').then(m=>{m.cats.forEach(c=>$('#cat').insertAdjacentHTML('beforeend',`<option>${e(c)}</option>`));
$('#stats').innerHTML=[[m.total.toLocaleString(),'papers in the dataset'],[m.cats.length,'research domains'],[m.authors.toLocaleString(),'authors in network']].map(s=>`<div class="card stat"><b>${s[0]}</b>${s[1]}</div>`).join('')});
api('saved').then(l=>l.forEach(p=>saved.add(p.id)));hist();

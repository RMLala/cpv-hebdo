import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import fs from 'node:fs';import {IDBFactory} from 'fake-indexeddb';import {issuer,paidFixture} from './fixtures/issuer/service.mjs';
const html=fs.readFileSync(new URL('../Standalone.html',import.meta.url),'utf8'),script=html.match(/<script>([\s\S]*?)<\/script>/)[1].replace("document.querySelector('main').inert=true;boot();",'');
async function device(service,id,activation,prior){
 const store=prior?.store||new Map(),idb=prior?.idb||new IDBFactory(),elements=new Map();store.set('cpv-installation-v3',id);const messages=[];
 const get=id=>{if(!elements.has(id))elements.set(id,{value:id==='style'?'original':'',classList:{add(){},remove(){},toggle(){}},style:{setProperty(){}},dataset:{},setAttribute(){},textContent:'',innerHTML:''});return elements.get(id)};
 const context={window:{structuredClone,addEventListener(){}},document:{getElementById:get,querySelectorAll:()=>[],querySelector:()=>({inert:false}),addEventListener(){}},navigator:{onLine:true},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},crypto,structuredClone,TextEncoder,TextDecoder,Blob,File,Buffer,atob,btoa,Date,Intl,JSON,AbortSignal,indexedDB:idb,confirm:()=>true,console,assert,setTimeout,clearTimeout,setInterval:()=>0,fetch:async(url,options={})=>{const u=new URL(url);assert.equal(u.origin,'https://appforge-license-api.appforge-licences-mg.workers.dev');assert.equal(u.searchParams.has('token'),false);const response=await service.api(u.pathname+u.search,options.method||'GET',options.body?JSON.parse(options.body):null,options.headers?.Authorization?.replace(/^Bearer /,''));return new Response(JSON.stringify(response.body),{status:response.status})}};
 context.globalThis=context;vm.createContext(context);vm.runInContext(script,context);const run=code=>vm.runInContext(code,context);
 await run(`(async()=>{blobData20=async blob=>'data:'+blob.type+';base64,'+Buffer.from(await blob.arrayBuffer()).toString('base64');await vaultInit3();const saved=await dbRead();state=validateState(saved||{version:1,draft:initial,history:[]});inputs=()=>{};formatControls20=()=>{};refreshCloud4=message=>{if(message)lastCloudError4=message};bootComplete3=true;})()`);
 context.activation=activation;await run(`(async()=>{license3={endpoint:ISSUER4,product:PRODUCT4,token:activation.token,key:activation.publicKey,checkedAt:Date.now()};await storeLicense3()})()`);
 return{run,context,store,idb,close:async()=>{await run(`(async()=>{clearTimeout(syncTimer20);clearTimeout(storageTimer);await saveSerial;db?.close()})()`);}};
}
const activate=(s,l,id)=>s.api('/v1/activate','POST',{productId:'CPV-HEBDO',code:l.code,installationId:id}).then(r=>{assert.equal(r.status,200);return r.body});
test('Client + official Worker + SQLite: automatic access, two devices, offline persistence/restart, reconnect, conflicts, no duplicates or token exports',async()=>{
 const s=await issuer(),license=await paidFixture(s),idA='synthetic-client-device-A',idB='synthetic-client-device-B';let a,b,restarted;
 try{
 const actA=await activate(s,license,idA),actB=await activate(s,license,idB);a=await device(s,idA,actA);b=await device(s,idB,actB);
 await a.run(`(async()=>{state.draft.page='Synthetic A';state.draft.captures=[{name:'Synthetic PNG',include:true,data:'data:image/png;base64,iVBORw0KGgo='}];await dbWrite(state);await startCloud4(activation.cloudAccess);assert.equal(cloudConnected4(),true);assert.equal(syncMeta4().pending,false);})()`);
 await b.run(`(async()=>{await startCloud4(activation.cloudAccess);assert.equal(cloudConflict20,true);await restoreCloud20(false);assert.equal(state.draft.page,'Synthetic A');assert.equal(state.draft.captures.length,1)})()`);
 await a.run(`(async()=>{navigator.onLine=false;state.draft.after=35678;localGeneration20++;syncMeta4().pending=true;await dbWrite(state);await syncCloud20();assert.equal(syncMeta4().pending,true)})()`);
 await a.close();restarted=await device(s,idA,actA,a);a=null;await restarted.run(`(async()=>{assert.equal(state.draft.after,35678);assert.equal(syncMeta4().pending,true);await startCloud4();assert.equal(syncMeta4().pending,false);assert.equal(cloudConnected4(),true);session4.expires=Date.now()-1;await connectCloud4();assert.ok(session4.expires>Date.now())})()`);
 await b.run(`(async()=>{await syncCloud20();assert.equal(state.draft.after,35678);assert.equal(state.draft.captures.length,1);})()`);
 const beforeCount=s.sql.prepare("SELECT COUNT(*) n FROM cloud_snapshots WHERE resource LIKE 'cpv-c-%'").get().n;
 await restarted.run(`(async()=>{await syncCloud20();await syncCloud20();})()`);
 assert.equal(s.sql.prepare("SELECT COUNT(*) n FROM cloud_snapshots WHERE resource LIKE 'cpv-c-%'").get().n,beforeCount);
 await b.run(`state.draft.page='Changed on B';localGeneration20++;syncMeta4().pending=true`);
 await restarted.run(`(async()=>{state.draft.page='Changed on A';localGeneration20++;syncMeta4().pending=true;await syncCloud20()})()`);
 await b.run(`(async()=>{await syncCloud20();assert.equal(cloudConflict20,true);assert.equal(state.draft.page,'Changed on B');})()`);
 const allPayloads=s.sql.prepare('SELECT payload FROM cloud_snapshots').all().map(r=>r.payload).join('');for(const token of [actA.token,actA.cloudAccess.token,license.code])assert.equal(allPayloads.includes(token),false);
 assert.equal(allPayloads.includes('cloudSync4'),false);assert.equal(allPayloads.includes('cloudLink'),false);
 for(const value of restarted.store.values()){assert.equal(value.includes(actA.cloudAccess.token),false);assert.equal(value.includes(actA.token),false);}
 await restarted.run(`(async()=>{let output;backupChoice3=async()=>false;download=async blob=>output=await blob.text();backupDirectory=null;await backupMode20(false);const body=JSON.parse(JSON.parse(output).json);assert.equal(body.state.cloudSync4,undefined);assert.equal(body.state.cloudLink,undefined);assert.equal(output.includes(license3.token),false)})()`);
 await b.run(`(async()=>{cloudLoaded20=false;await readCloud20();const original=cloudReadChunks20;let changed=false;cloudReadChunks20=async ids=>{const data=await original(ids);if(!changed){state.draft.page='Typed during restore';localGeneration20++;changed=true;}return data};await assert.rejects(()=>restoreCloud20(false),/modifications locales/i);assert.equal(state.draft.page,'Typed during restore');cloudReadChunks20=original;})()`);
 await restarted.run(`(async()=>{const previous=license3.token;cloudBusy20=true;await licenseAction3('activate');assert.equal(license3.token,previous);cloudBusy20=false;})()`);
 const otherLicense=await paidFixture(s),otherActivation=await activate(s,otherLicense,idA);restarted.context.otherAccess=otherActivation.cloudAccess;await restarted.run(`(async()=>{await assert.rejects(()=>verifyCloudToken4(otherAccess),/droits Cloud/);assert.equal(cloudConnected4(),true)})()`);
 await s.admin('/admin/licenses/'+license.id+'/revoke','POST',{});
 await restarted.run(`(async()=>{state.draft.page='Offline data retained after revocation';localGeneration20++;syncMeta4().pending=true;await syncCloud20();assert.equal(cloudConnected4(),false);assert.equal(cloudStopped4,true);assert.equal(state.draft.page,'Offline data retained after revocation');assert.equal(syncMeta4().pending,true);})()`);
 }finally{if(a)await a.close();if(b)await b.close();if(restarted)await restarted.close();s.close();}
});

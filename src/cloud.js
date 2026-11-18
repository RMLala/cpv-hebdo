// AppForge license-bound Cloud contract: RMLala/appforg PR #8, commit 8f2786f.
// No account, organization, server secret, token in URL, backup or report.
const ISSUER4='https://appforge-license-api.appforge-licences-mg.workers.dev',PRODUCT4='CPV-HEBDO';
let session4=null,connectPromise4=null,syncPromise4=null,retry4=2000,poll4=null,lastCloudError4='',cloudScope4='',cloudStopped4=false;
const nativePending4=new Map();
function syncMeta4(){return state.cloudSync4||(state.cloudSync4={pending:true,lastSuccess:'',scope:''})}
const validateBefore4=validateState;
validateState=function(value){const n=validateBefore4(value),s=value.cloudSync4;n.cloudSync4={pending:s?.pending!==false,lastSuccess:typeof s?.lastSuccess==='string'&&Number.isFinite(Date.parse(s.lastSuccess))?s.lastSuccess:'',scope:typeof s?.scope==='string'?s.scope.slice(0,180):''};return n};
function cloudConnected4(){return !!session4&&session4.expires>Date.now()&&navigator.onLine!==false&&!cloudStopped4}
function refreshCloud4(message){
 if(message!==undefined)lastCloudError4=message;
 const el=$('cloudStatus20');if(!el)return;const meta=syncMeta4();
 const status=navigator.onLine===false?'Hors ligne · données conservées sur cet appareil.':cloudStopped4?'Accès Cloud suspendu · données locales conservées.':cloudConnected4()?'Accès Cloud vérifié · jeton temporaire valide.':license3?.token?'Cloud non connecté · vérification nécessaire.':'Cloud inactif · activez une licence éligible.';
 el.textContent=status+'\n'+(meta.pending?'Modifications en attente de synchronisation.':'Aucune modification locale en attente.')+'\nDernière synchronisation : '+(meta.lastSuccess?new Date(meta.lastSuccess).toLocaleString('fr-FR'):'aucune sur cet appareil')+(lastCloudError4?'\n'+lastCloudError4:'');
 if($('cloudSync4'))$('cloudSync4').disabled=cloudBusy20||navigator.onLine===false||!license3?.token;
 if($('cloudConflict20'))$('cloudConflict20').hidden=!cloudConflict20;
 if($('licenseCloudRights4'))$('licenseCloudRights4').textContent='Droits Cloud : '+(cloudConnected4()?'accordés par le serveur.':cloudStopped4?'suspendus ou refusés.':'en attente de vérification serveur.');refreshSupport3();
}
function clearCloud4(suspended=false){session4=null;cloudToken20='';cloudStopped4=suspended;window.CpvNative?.clearCloudToken4?.();}
async function verifyCloudToken4(access){
 if(!access||typeof access.token!=='string'||access.token.length>8192)throw Error('Aucun jeton Cloud valide reçu.');
 const parts=access.token.split('.');if(parts.length!==2)throw Error('Jeton Cloud invalide.');
 const key=await crypto.subtle.importKey('jwk',license3.key,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
 if(!await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,fromUrl643(parts[1]),new TextEncoder().encode(parts[0])))throw Error('Signature Cloud invalide.');
 const c=JSON.parse(new TextDecoder().decode(fromUrl643(parts[0]))),lic=await offlineLicense3(),now=Date.now(),expiry=Date.parse(c.exp);
 if(!lic||c.v!==1||c.kind!=='cloud'||c.product!==PRODUCT4||c.install!==await hash20(installation3())||!Number.isFinite(expiry)||expiry<=now||expiry>now+7*3600000||!Number.isFinite(Date.parse(c.iat))||Date.parse(c.iat)>now+300000)throw Error('Jeton Cloud expiré ou associé à un autre appareil.');
 const expected=lic.kind==='trial'?'trial:'+c.install:lic.license;
 if(c.access!==expected||c.scope!==(lic.kind==='trial'?'trial':'license'))throw Error('Les droits Cloud ne correspondent pas à la licence.');
 const expires=Math.min(expiry,lic.exp?Date.parse(lic.exp):Infinity);
 if(window.CpvNative?.storeCloudToken4&&!CpvNative.storeCloudToken4(access.token,expires))throw Error('Stockage sécurisé du jeton indisponible.');
 session4={token:window.CpvNative?.licenseCloudRequest4?null:access.token,expires,scope:PRODUCT4+':'+c.access};cloudToken20='verified-license-session';cloudOrg20=session4.scope;cloudScope4=session4.scope;cloudStopped4=false;
 return session4;
}
async function connectCloud4(force=false,access=null){
 if(navigator.onLine===false)throw Error('Hors ligne. Reprise automatique au retour du réseau.');
 if(!force&&session4?.expires>Date.now()+60000&&!cloudStopped4)return session4;
 if(connectPromise4)return connectPromise4;
 connectPromise4=(async()=>{try{
  if(!license3?.token)throw Error('Activez une licence éligible au Cloud.');
  if(!license3.key){const config=await licenseAPI3('/v1/config');license3.key=config.publicKey;}
  await claims3(license3.token,license3.key,PRODUCT4,installation3());
  if(!access){const result=await licenseAPI3('/v1/status',{productId:PRODUCT4,installationId:installation3(),token:license3.token});if(result.active!==true)throw Object.assign(Error('Licence inactive.'),{status:403});license3.checkedAt=Date.now();license3.revoked=false;await storeLicense3();access=await licenseAPI3('/v1/cloud/token',{productId:PRODUCT4,installationId:installation3(),licenseToken:license3.token});}
  await verifyCloudToken4(access);lastCloudError4='';retry4=2000;refreshCloud4();return session4;
 }catch(e){clearCloud4(e.status===403);if(e.status===403){license3.revoked=true;await storeLicense3();}refreshCloud4(e.status===404?'Le service Cloud lié aux licences n’est pas encore disponible.':e.message);throw e;}finally{connectPromise4=null}})();
 return connectPromise4;
}
window.cpvLicenseCloudResult4=(id,raw)=>{const pending=nativePending4.get(id);if(!pending)return;nativePending4.delete(id);clearTimeout(pending.timer);try{const r=JSON.parse(raw);if(r.error)throw Error(r.error);const body=JSON.parse(r.body||'{}');if(r.status>=400)throw Object.assign(Error('Cloud : '+r.status),{status:r.status});pending.resolve(body)}catch(e){pending.reject(e)}};
async function cloudTransport4(resource,method='GET',body){
 if(!/^[A-Za-z0-9_-]{1,80}$/.test(resource)||!['GET','PUT'].includes(method))throw Error('Requête Cloud invalide.');
 if(window.CpvNative?.licenseCloudRequest4)return new Promise((resolve,reject)=>{const id=crypto.randomUUID(),timer=setTimeout(()=>{nativePending4.delete(id);reject(Error('Service Cloud injoignable.'))},45000);nativePending4.set(id,{resolve,reject,timer});CpvNative.licenseCloudRequest4(id,resource,method,body?JSON.stringify(body):'')});
 const response=await fetch(ISSUER4+'/v1/cloud/snapshot?resource='+encodeURIComponent(resource),{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+session4.token},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)}),result=await response.json().catch(()=>({}));
 if(!response.ok)throw Object.assign(Error('Cloud : '+response.status),{status:response.status});return result;
}
cloudRoute20=function(resource=APP_ID20){return resource};
cloudAPI20=async function(resource,method='GET',body){
 await connectCloud4();if(!await offlineLicense3()){clearCloud4(true);await refreshLicense3();throw Error('Licence expirée : données locales conservées.');}
 try{return await cloudTransport4(resource,method,body)}catch(e){
  if(e.status===401){clearCloud4();await connectCloud4(true);return cloudTransport4(resource,method,body);}
  if(e.status===403){clearCloud4(true);try{await licenseAPI3('/v1/status',{productId:PRODUCT4,installationId:installation3(),token:license3.token});}catch(statusError){if(statusError.status===403){license3.revoked=true;await storeLicense3();await refreshLicense3();}}throw Error('Licence, droits Cloud ou appareil refusés. Contactez le support.');}
  if(e.status===413)throw Error('Quota par requête dépassé. Archivez les anciennes captures.');
  if(e.status===429)throw Error('Quota du service atteint. Réessayez plus tard.');throw e;
 }
};
cloudStatus20=function(text){refreshCloud4(text)};
cloudPayload20=async function(){const{packed,assets}=await packState20(validateState(state));delete packed.cloudLink;delete packed.cloudSync4;return{packed,assets,hash:await hash20(JSON.stringify(packed))}};
function conflict4(message){cloudConflict20=true;refreshCloud4(message||'Conflit entre appareils : choisissez explicitement la copie à conserver. Aucune donnée n’a été effacée.');}
async function saveSyncMeta4(hash,revision){state.cloudLink={org:cloudScope4,revision,hash};const m=syncMeta4();m.lastSuccess=new Date().toISOString();m.scope=cloudScope4;saveSerial=saveSerial.catch(()=>{}).then(()=>dbWrite(structuredClone(state)));await saveSerial;}
readCloud20=async function(){await connectCloud4();const root=await cloudAPI20(cloudRoute20());cloudRoot20=root.snapshot||null;cloudRev20=Number(root.revision||0);cloudLoaded20=true;return root};
pushCloud20=async function(manual=true){
 if(cloudBusy20)return;await connectCloud4();if(manual&&!confirm('Conserver les données de cet appareil dans le Cloud de la licence active ? La copie distante sera remplacée.'))return;
 if(!cloudLoaded20)await readCloud20();cloudBusy20=true;const generation=localGeneration20;
 try{
  const p=await cloudPayload20(),manifest={schema:2,appId:APP_ID20,stateChunks:[],assets:[],hash:p.hash};
  for(const part of chunkText20(JSON.stringify(p.packed)))manifest.stateChunks.push(await cloudChunk20(part));
  for(const [id,blob]of p.assets){const parts=[];for(const text of chunkText20(await blobData20(blob)))parts.push(await cloudChunk20(text));manifest.assets.push({id,parts});}
  if(new TextEncoder().encode(JSON.stringify(manifest)).length>400000)throw Error('Trop de captures : archivez les anciennes semaines.');
  const result=await cloudAPI20(cloudRoute20(),'PUT',{expectedRevision:cloudRev20,snapshot:manifest});if(!Number.isSafeInteger(result.revision)||result.revision<=cloudRev20)throw Error('Révision Cloud invalide.');
  cloudRev20=result.revision;cloudRoot20=manifest;cloudLoaded20=true;cloudConflict20=false;syncMeta4().pending=generation!==localGeneration20;await saveSyncMeta4(p.hash,result.revision);refreshCloud4('Synchronisation réussie.');
 }catch(e){if(e.status===409){cloudLoaded20=false;conflict4();}else throw e;}finally{cloudBusy20=false;refreshCloud4();}
};
restoreCloud20=async function(automatic=false){
 if(cloudBusy20)return;await connectCloud4();if(!cloudLoaded20)await readCloud20();if(!cloudRoot20)return;
 if(!automatic&&!confirm('Utiliser la copie Cloud de cette licence sur cet appareil ? Les données locales seront remplacées. Exportez une sauvegarde avant de continuer.'))return;
 const root=structuredClone(cloudRoot20),rev=cloudRev20,generation=localGeneration20;if(root.schema!==2||root.appId!==APP_ID20||!Array.isArray(root.assets)||root.assets.length>6000)throw Error('Format Cloud incompatible.');cloudBusy20=true;
 try{
  const raw=await cloudReadChunks20(root.stateChunks);if(await hash20(raw)!==root.hash)throw Error('Copie Cloud altérée.');const assets=new Map();
  for(const a of root.assets){if(!/^[a-f0-9]{64}$/.test(a.id)||assets.has(a.id))throw Error('Capture Cloud invalide.');const text=await cloudReadChunks20(a.parts);if(await hash20(text)!==a.id)throw Error('Capture Cloud altérée.');assets.set(a.id,text);}
  const next=validateState(await hydrate20(JSON.parse(raw),id=>{if(!assets.has(id))throw Error('Capture Cloud manquante.');return assets.get(id)}));
  if(generation!==localGeneration20)throw Error('Des modifications locales sont intervenues. Restauration annulée.');
  next.cloudLink={org:cloudScope4,revision:rev,hash:root.hash};next.cloudSync4={pending:false,scope:cloudScope4,lastSuccess:new Date().toISOString()};
  // Persist successfully before replacing the in-memory state.
  const main=document.querySelector('main'),wasInert=main.inert;main.inert=true;try{clearTimeout(storageTimer);await saveSerial;if(generation!==localGeneration20)throw Error('Modifications locales détectées. Restauration annulée.');await dbWrite(structuredClone(next),()=>generation===localGeneration20);if(generation!==localGeneration20){await dbWrite(structuredClone(state));throw Error('Modifications locales conservées. Restauration annulée.');}state=next;localGeneration20++;}finally{main.inert=wasInert;}cloudConflict20=false;inputs();formatControls20();refreshCloud4('Copie Cloud récupérée.');
 }finally{cloudBusy20=false;refreshCloud4();}
};
async function syncOnce4(){
 if(navigator.onLine===false){refreshCloud4();return;}await connectCloud4();
 if(cloudConflict20||cloudBusy20)return;
 const meta=syncMeta4();if(meta.scope&&meta.scope!==cloudScope4){conflict4('La licence active a changé. Choisissez explicitement les données à conserver avant tout transfert.');return;}
 const root=await readCloud20(),local=await cloudPayload20(),link=state.cloudLink?.org===cloudScope4?state.cloudLink:null;
 if(root.snapshot?.hash===local.hash){syncMeta4().pending=false;await saveSyncMeta4(local.hash,root.revision);lastCloudError4='';refreshCloud4();return;}
 if(root.snapshot&&!link){conflict4('Une copie existe déjà pour cette licence. Choisissez Cet appareil ou Cloud avant la première synchronisation.');return;}
 if(link&&root.revision!==link.revision){if(local.hash===link.hash){await restoreCloud20(true);return;}conflict4();return;}
 await pushCloud20(false);
}
syncCloud20=async function(){
 if(syncPromise4)return syncPromise4;if(!license3?.token||cloudStopped4)return refreshCloud4();
 syncPromise4=(async()=>{try{await syncOnce4();retry4=2000;}catch(e){refreshCloud4(e.message);if(!cloudStopped4&&license3?.token){clearTimeout(syncTimer20);syncTimer20=setTimeout(syncCloud20,retry4);retry4=Math.min(60000,retry4*2);}}finally{syncPromise4=null}})();return syncPromise4;
};
scheduleSync20=function(){if(!state)return;syncMeta4().pending=true;if(!bootComplete3||!license3?.token)return;clearTimeout(syncTimer20);syncTimer20=setTimeout(syncCloud20,1500)};
async function startCloud4(access=null){
 if(!license3?.token)return refreshCloud4();cloudStopped4=false;
 try{await connectCloud4(true,access);await syncCloud20();}catch{}
 if(!poll4)poll4=setInterval(()=>{if(navigator.onLine!==false&&!cloudStopped4)syncCloud20();},60000);
}
function setupCloud4(){
 $('cloudSync4').onclick=async()=>{cloudStopped4=false;cloudLoaded20=false;try{await connectCloud4(true);await syncCloud20();}catch(e){refreshCloud4(e.message)}};
 $('cloudPush20').onclick=async()=>{try{cloudLoaded20=false;await readCloud20();await pushCloud20(true)}catch(e){refreshCloud4(e.message)}};
 $('cloudRestore20').onclick=async()=>{try{cloudLoaded20=false;await readCloud20();await restoreCloud20(false)}catch(e){refreshCloud4(e.message)}};refreshCloud4();
}
window.addEventListener('online',()=>{retry4=2000;cloudStopped4=false;startCloud4()});
window.addEventListener('offline',()=>refreshCloud4());

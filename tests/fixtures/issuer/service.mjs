import assert from 'node:assert/strict';import {DatabaseSync} from 'node:sqlite';import fs from 'node:fs';import worker from './worker.mjs';
export async function issuer(){
 const sql=new DatabaseSync(':memory:');sql.exec(fs.readFileSync(new URL('./schema.sql',import.meta.url),'utf8'));
 const DB={prepare(query){let values=[];return{bind(...args){values=args;return this},async first(){return sql.prepare(query).get(...values)||null},async all(){return{results:sql.prepare(query).all(...values)}},async run(){const r=sql.prepare(query).run(...values);return{meta:{changes:Number(r.changes)}}}}},async batch(rows){return Promise.all(rows.map(r=>r.run()))}};
 const key=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);const env={DB,ADMIN_API_TOKEN:crypto.randomUUID(),CODE_HASH_PEPPER:crypto.randomUUID(),LICENSE_SIGNING_PRIVATE_JWK:JSON.stringify(await crypto.subtle.exportKey('jwk',key.privateKey))};
 const api=async(path,method='GET',body,token)=>{const r=await worker.fetch(new Request('https://issuer.test'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})}),env);return{status:r.status,body:await r.json()}};
 const admin=(path,method,body)=>api(path,method,body,env.ADMIN_API_TOKEN);
 await admin('/admin/apps','POST',{productId:'CPV-HEBDO',name:'CPV synthetic fixture'});
 return{sql,env,api,admin,close:()=>sql.close()};
}
export async function paidFixture(service,maxDevices=2){const r=await service.admin('/admin/licenses','POST',{productId:'CPV-HEBDO',customerName:'Synthetic QA — no real customer',plan:'annual',paymentConfirmed:true,paymentReference:'SYNTHETIC-NOT-A-MVOLA-PAYMENT',maxDevices});assert.equal(r.status,201);return r.body;}

// Executes the pinned official Worker against real SQLite, with synthetic fixtures.
// This is NOT a production D1 test and does NOT validate a real MVola payment.
import {test} from 'node:test';import assert from 'node:assert/strict';import {issuer,paidFixture} from './fixtures/issuer/service.mjs';
const activate=(s,code,installationId)=>s.api('/v1/activate','POST',{productId:'CPV-HEBDO',code,installationId});
const renew=(s,token,installationId)=>s.api('/v1/cloud/token','POST',{productId:'CPV-HEBDO',licenseToken:token,installationId});
const endpoint='/v1/cloud/snapshot?resource=cpv-hebdo-v1';
test('Official Worker: payment confirmation, activation, token renewal, 2 devices, isolation, revocation and quotas (synthetic SQLite)',async()=>{
 const s=await issuer();try{
 const unpaid=await s.admin('/admin/licenses','POST',{productId:'CPV-HEBDO',customerName:'Synthetic unpaid',plan:'annual',paymentConfirmed:false,paymentReference:'NOT-PAID'});assert.equal(unpaid.status,400);
 const license=await paidFixture(s),a=await activate(s,license.code,'synthetic-device-one-0001'),b=await activate(s,license.code,'synthetic-device-two-0002');assert.equal(a.status,200);assert.equal(b.status,200);assert.ok(a.body.cloudAccess.token);assert.equal((await activate(s,license.code,'synthetic-device-three-0003')).status,403);
 const renewed=await renew(s,a.body.token,'synthetic-device-one-0001');assert.equal(renewed.status,200);assert.equal((await renew(s,a.body.token,'synthetic-device-three-0003')).status,403);
 const token=a.body.cloudAccess.token;assert.equal((await s.api(endpoint,'GET',null,token)).body.snapshot,null);
 const write=await s.api(endpoint,'PUT',{expectedRevision:0,snapshot:{report:'fixture',value:7}},token);assert.equal(write.status,200);
 assert.deepEqual((await s.api(endpoint,'GET',null,b.body.cloudAccess.token)).body.snapshot,{report:'fixture',value:7});
 assert.equal((await s.api(endpoint,'PUT',{expectedRevision:0,snapshot:{value:99}},token)).status,409);assert.equal(s.sql.prepare('SELECT COUNT(*) n FROM cloud_snapshots').get().n,1);
 const second=await paidFixture(s),other=await activate(s,second.code,'synthetic-other-license-0001');assert.equal((await s.api(endpoint,'GET',null,other.body.cloudAccess.token)).body.snapshot,null);
 const parts=token.split('.');parts[0]=Buffer.from(JSON.stringify({...JSON.parse(Buffer.from(parts[0],'base64url')),access:second.id})).toString('base64url');assert.equal((await s.api(endpoint,'GET',null,parts.join('.'))).status,401);
 assert.equal((await s.api(endpoint,'PUT',{expectedRevision:1,snapshot:{large:'x'.repeat(450000)}},token)).status,413);
 s.sql.prepare('DELETE FROM activations WHERE license_id=? AND installation_hash IN (SELECT installation_hash FROM activations WHERE license_id=? ORDER BY id LIMIT 1)').run(license.id,license.id);assert.equal((await s.api(endpoint,'GET',null,token)).status,403);
 const revoke=await s.admin('/admin/licenses/'+license.id+'/revoke','POST',{});assert.equal(revoke.status,200);assert.equal((await s.api(endpoint,'GET',null,b.body.cloudAccess.token)).status,403);assert.equal((await renew(s,b.body.token,'synthetic-device-two-0002')).status,403);
 }finally{s.close();}
});
test('Official Worker checks license expiration and suspended product on every operation',async()=>{const s=await issuer();try{const l=await paidFixture(s),a=await activate(s,l.code,'synthetic-expiry-device-0001');s.sql.prepare("UPDATE licenses SET expires_at='2020-01-01T00:00:00Z' WHERE id=?").run(l.id);assert.equal((await s.api(endpoint,'GET',null,a.body.cloudAccess.token)).status,403);assert.equal((await renew(s,a.body.token,'synthetic-expiry-device-0001')).status,403);s.sql.prepare('UPDATE licenses SET expires_at=NULL WHERE id=?').run(l.id);s.sql.prepare("UPDATE products SET status='inactive' WHERE product_id='CPV-HEBDO'").run();assert.equal((await s.api(endpoint,'GET',null,a.body.cloudAccess.token)).status,403);}finally{s.close();}});

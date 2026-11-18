// Query only the debug WebView through Android's local DevTools socket.
const [url,expression]=process.argv.slice(2),ws=new WebSocket(url);
const timer=setTimeout(()=>{console.error('DevTools request timeout');process.exit(1)},30000);
ws.addEventListener('open',()=>ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}})));
ws.addEventListener('message',event=>{const r=JSON.parse(event.data);if(r.id!==1)return;if(r.error||r.result?.exceptionDetails){console.error(JSON.stringify(r));process.exit(1)}console.log(JSON.stringify(r.result.result.value??null));clearTimeout(timer);ws.close()});

package mg.rmlala.cpv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends androidx.fragment.app.FragmentActivity {
    private WebView web;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private TextRecognizer recognizer;
    private boolean destroyed = false;
    private PinGate pinGate;
    private long stoppedAt;
    private boolean contentLoaded;
    private boolean securityEditing;
    private ValueCallback<Uri[]> chooser;
    private static final int PICK_FILE = 501, STORAGE = 502;
    private static final String LOCAL_HOST = "appassets.androidplatform.net";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff183858);
        getWindow().setNavigationBarColor(0xff183858);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return navigate(r.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return navigate(Uri.parse(url)); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "image/jpeg", "image/png", "image/webp"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, PICK_FILE); }
                catch (Exception e) { chooser.onReceiveValue(null); chooser = null; toast("Aucun sélecteur de fichiers disponible."); }
                return true;
            }
        });
        web.addJavascriptInterface(new NativeFiles(), "CpvNative");
        try {
            pinGate = new PinGate(this);
            showGate();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage("Le coffre Android ne peut pas démarrer. Aucune donnée n’a été effacée.")
                .setPositiveButton("Fermer", (d,w)->finish()).setCancelable(false).show();
        }
    }
    private void showGate(){pinGate.show(()->{setContentView(web);if(!contentLoaded){contentLoaded=true;web.loadUrl("https://"+LOCAL_HOST+"/assets/index.html");}});}
    private boolean authorized(){return pinGate!=null && pinGate.isUnlocked();}
    private boolean navigate(Uri uri) {
        if ("https".equals(uri.getScheme()) && LOCAL_HOST.equals(uri.getHost()) && "/assets/index.html".equals(uri.getPath())) return false;
        if ("https".equals(uri.getScheme()) && "wa.me".equals(uri.getHost())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception e) { toast("Installez WhatsApp ou un navigateur pour envoyer le message."); }
        }
        return true; // Remote pages never receive the JavaScript interface.
    }
    private void toast(String msg) { runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()); }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == PICK_FILE && chooser != null) {
            if (result == Activity.RESULT_OK && data != null && data.getClipData() != null) {
                ClipData clips=data.getClipData();int count=Math.min(12,clips.getItemCount());Uri[] selected=new Uri[count];for(int i=0;i<count;i++)selected[i]=clips.getItemAt(i).getUri();chooser.onReceiveValue(selected);
            } else chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            chooser = null;
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == STORAGE) toast(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? "Accès autorisé. Relancez la sauvegarde." : "Accès refusé. Vous pouvez autoriser le stockage dans les paramètres Android.");
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){var d=document.getElementById('drawer');if(d&&!d.hidden){drawer(false);return true}return false})()", value -> {
            if (!"true".equals(value)) new AlertDialog.Builder(this).setMessage("Fermer CPV Rapport Hebdo ?").setPositiveButton("Fermer", (d, w) -> finish()).setNegativeButton("Rester", null).show();
        });
    }
    private void callback(String function, String requestId, JSONObject result) {
        if (destroyed) return;
        runOnUiThread(() -> { if (!destroyed) web.evaluateJavascript("window."+function+"("+JSONObject.quote(requestId)+","+JSONObject.quote(result.toString())+")",null); });
    }
    private void errorCallback(String function,String requestId,String message){try{callback(function,requestId,new JSONObject().put("error",message));}catch(Exception ignored){}}
    @Override protected void onResume(){super.onResume();if(!securityEditing && pinGate!=null && pinGate.enabled() && stoppedAt>0 && System.currentTimeMillis()-stoppedAt>=60000){pinGate.lock();showGate();}stoppedAt=0;if(web!=null && authorized())web.evaluateJavascript("typeof scheduleSync20==='function'&&scheduleSync20()",null);}
    @Override protected void onStop(){stoppedAt=System.currentTimeMillis();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;if(recognizer!=null)recognizer.close();io.shutdownNow();if(web!=null){web.removeJavascriptInterface("CpvNative");web.destroy();}super.onDestroy();}
    public class NativeFiles {
        @JavascriptInterface public String securityStatus(){
            try{return new JSONObject().put("enabled",pinGate.enabled()).put("biometric",pinGate.biometricEnabled()).put("available",pinGate.biometricAvailable()).toString();}catch(Exception e){return "{}";}
        }
        @JavascriptInterface public void securityAction(String action){
            if(!authorized())return;
            runOnUiThread(()->{securityEditing=true;pinGate.configure(action,()->{securityEditing=false;stoppedAt=0;setContentView(web);web.evaluateJavascript("refreshSecurity3()",null);});});
        }
        @JavascriptInterface public void lockNow(){if(!authorized()||!pinGate.enabled())return;runOnUiThread(()->{pinGate.lock();showGate();});}

        @JavascriptInterface public String vaultSecret(){try{return pinGate==null?"":pinGate.vaultSecret();}catch(Exception e){return "";}}
        @JavascriptInterface public String installationId(){
            if(!authorized())return "";
            try{String id=android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);
                if(id==null||id.isEmpty())throw new Exception();byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(("mg.rmlala.cpv:"+id).getBytes(StandardCharsets.UTF_8));
                StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
            }catch(Exception e){return "";}
        }
        @JavascriptInterface public void licenseRequest(String requestId,String endpoint,String path,String body){
            if(!authorized()){errorCallback("cpvLicenseResult3",requestId,"Application verrouillée.");return;}
            io.execute(()->{HttpURLConnection connection=null;try{
                if(!endpoint.equals("https://appforge-license-api.appforge-licences-mg.workers.dev"))throw new Exception("Service refusé.");
                URL base=new URL(endpoint);
                if(!base.getProtocol().equals("https")||!base.getHost().matches("[a-z0-9.-]+\\.workers\\.dev")||base.getPort()!=-1||base.getUserInfo()!=null||base.getQuery()!=null||base.getRef()!=null||!(base.getPath().isEmpty()||base.getPath().equals("/")))throw new Exception("Service refusé.");
                if(!path.matches("/v1/(config|trial|activate|status|cloud/token)")||body==null||body.length()>16000)throw new Exception("Route refusée.");
                connection=(HttpURLConnection)new URL(endpoint+path).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
                connection.setRequestMethod(path.equals("/v1/config")?"GET":"POST");connection.setRequestProperty("Content-Type","application/json");
                if(!path.equals("/v1/config")){connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                int status=connection.getResponseCode();InputStream stream=status<400?connection.getInputStream():connection.getErrorStream();ByteArrayOutputStream result=new ByteArrayOutputStream();
                if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(result.size()+n>32000)throw new Exception("Réponse trop volumineuse.");result.write(buffer,0,n);}}
                callback("cpvLicenseResult3",requestId,new JSONObject().put("status",status).put("body",result.toString("UTF-8")));
            }catch(Exception e){errorCallback("cpvLicenseResult3",requestId,"Service de licences inaccessible.");}finally{if(connection!=null)connection.disconnect();}});
        }
        @JavascriptInterface public void openSupport(){if(!authorized())return;runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:lalainaadclick@gmail.com?subject=Support%20CPV%202.1")));}catch(Exception e){toast("Aucune application e-mail disponible.");}});}
        @JavascriptInterface public String saveFile(String data, String requestedName, String mime, boolean share) {
            Uri pendingUri = null;
            try {
                if(!authorized())throw new Exception("Déverrouillez l’application.");
                if (data == null || data.length() > 85000000) throw new Exception("Fichier trop volumineux.");
                if (!mime.equals("application/json") && !mime.equals("image/png") && !mime.equals("application/zip") && !mime.startsWith("text/csv")) throw new Exception("Type de fichier refusé.");
                String name = requestedName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (name.length() < 1 || name.length() > 180) throw new Exception("Nom de fichier invalide.");
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                String stamp = String.valueOf(System.currentTimeMillis());
                int dot = name.lastIndexOf('.');
                name = dot > 0 ? name.substring(0, dot) + "-" + stamp + name.substring(dot) : name + "-" + stamp;
                String path;
                Uri uri;
                if (share) {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Dossier de partage indisponible.");
                    File[] old = dir.listFiles();
                    if (old != null) for (File f : old) if (System.currentTimeMillis() - f.lastModified() > 86400000L) f.delete();
                    File file = new File(dir, name);
                    try (OutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                    uri = FileProvider.getUriForFile(MainActivity.this, getPackageName()+".files", file);
                    path = "Partage du rapport";
                } else if (Build.VERSION.SDK_INT >= 29) {
                    String sub = mime.equals("application/json") ? "Backup" : "CPV";
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + sub);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Téléchargements indisponibles.");
                    pendingUri = uri;
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("Écriture impossible.");
                        out.write(bytes);
                    }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    pendingUri = null;
                    path = "Download/"+sub+"/"+name;
                } else {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE));
                        throw new Exception("Autorisez le stockage puis relancez la sauvegarde (Android 6 à 9).");
                    }
                    String sub = mime.equals("application/json") ? "Backup" : "CPV";
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub);
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Création du dossier impossible.");
                    File file = new File(dir, name);
                    try (OutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                    uri = Uri.fromFile(file);
                    path = "Download/"+sub+"/"+name;
                }
                if (share) {
                    final Uri shared = uri;
                    final String type = mime;
                    runOnUiThread(() -> {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(type);
                        send.putExtra(Intent.EXTRA_STREAM, shared);
                        send.setClipData(ClipData.newUri(getContentResolver(), "Rapport CPV", shared));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Partager le rapport complet"));
                    });
                }
                return new JSONObject().put("ok", true).put("path", path).toString();
            } catch (Exception e) {
                if (pendingUri != null) getContentResolver().delete(pendingUri, null, null);
                try { return new JSONObject().put("ok", false).put("error", e.getMessage()).toString(); }
                catch (Exception ignored) { return "{\"ok\":false}"; }
            }
        }
        @JavascriptInterface public void recognizeText(String data, String requestId) {
            if(!authorized()){errorCallback("cpvOCRResult20",requestId,"Application verrouillée.");return;}
            io.execute(() -> {
                try {
                    if (data == null || data.length() > 12000000 || !data.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")) throw new Exception("Capture invalide.");
                    byte[] bytes = Base64.decode(data.substring(data.indexOf(',')+1), Base64.DEFAULT);
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if ((long)bounds.outWidth*bounds.outHeight > 12000000 || bounds.outWidth <= 0) throw new Exception("Image trop volumineuse.");
                    Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (image == null) throw new Exception("Image illisible.");
                    recognizer.process(InputImage.fromBitmap(image, 0))
                        .addOnSuccessListener(result -> { try { callback("cpvOCRResult20", requestId, new JSONObject().put("text",result.getText())); } catch (Exception ignored) {} image.recycle(); })
                        .addOnFailureListener(e -> { errorCallback("cpvOCRResult20", requestId, "Lecture OCR impossible."); image.recycle(); });
                } catch (Exception e) { errorCallback("cpvOCRResult20", requestId, e.getMessage()); }
            });
        }
        @JavascriptInterface public boolean storeCloudToken4(String token,long expires){try{pinGate.storeCloudToken(token,expires);return true;}catch(Exception e){return false;}}
        @JavascriptInterface public void clearCloudToken4(){if(authorized())pinGate.clearCloudToken();}
        @JavascriptInterface public void licenseCloudRequest4(String requestId,String resource,String method,String body){
            if(!authorized()){errorCallback("cpvLicenseCloudResult4",requestId,"Application verrouillée.");return;}
            io.execute(()->{HttpURLConnection connection=null;try{
                if(resource==null||!resource.matches("[A-Za-z0-9_-]{1,80}")||!(method.equals("GET")||method.equals("PUT"))||body==null||body.getBytes(StandardCharsets.UTF_8).length>450000)throw new Exception();
                String token=pinGate.cloudToken();
                connection=(HttpURLConnection)new URL("https://appforge-license-api.appforge-licences-mg.workers.dev/v1/cloud/snapshot?resource="+resource).openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(25000);connection.setRequestMethod(method);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Authorization","Bearer "+token);
                if(method.equals("PUT")){connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                int status=connection.getResponseCode();InputStream stream=status<400?connection.getInputStream():connection.getErrorStream();ByteArrayOutputStream result=new ByteArrayOutputStream();
                if(stream!=null)try(InputStream in=stream){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(result.size()+n>700000)throw new Exception();result.write(bytes,0,n);}}
                callback("cpvLicenseCloudResult4",requestId,new JSONObject().put("status",status).put("body",result.toString("UTF-8")));
            }catch(Exception e){errorCallback("cpvLicenseCloudResult4",requestId,"Service Cloud inaccessible. Les données locales sont conservées.");}finally{if(connection!=null)connection.disconnect();}});
        }
        @JavascriptInterface public String shareFiles(String manifest) {
            try {
                if(!authorized())throw new Exception("Déverrouillez l’application.");
                if(manifest==null || manifest.length()>80000000)throw new Exception("Fichiers trop volumineux.");
                JSONArray items=new JSONArray(manifest);if(items.length()<1||items.length()>12)throw new Exception("1 à 12 images maximum.");
                File dir=new File(getCacheDir(),"shared");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Dossier de partage indisponible.");
                ArrayList<Uri> uris=new ArrayList<>();long total=0;String stamp=String.valueOf(System.currentTimeMillis());
                for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String name=item.getString("name").replaceAll("[^a-zA-Z0-9._-]","_");String data=item.getString("data");if(name.length()>180||data.length()>24000000)throw new Exception("Image trop volumineuse.");byte[] bytes=Base64.decode(data,Base64.DEFAULT);total+=bytes.length;if(total>60000000)throw new Exception("Limite de partage dépassée.");File f=new File(dir,stamp+"-"+i+"-"+name);try(OutputStream out=new FileOutputStream(f)){out.write(bytes);}uris.add(FileProvider.getUriForFile(MainActivity.this,getPackageName()+".files",f));}
                runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_SEND_MULTIPLE);intent.setType("image/png");intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);ClipData clip=ClipData.newUri(getContentResolver(),"Rapport CPV",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));intent.setClipData(clip);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(intent,"Partager les cartes CPV"));}catch(Exception e){toast("Aucune application de partage disponible.");}});
                return new JSONObject().put("ok",true).toString();
            }catch(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()).toString();}catch(Exception ignored){return "{\"ok\":false}";}}
        }
        @JavascriptInterface public void printReport() {
            if(!authorized())return;
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
                if (manager != null) manager.print("CPV-Rapport", web.createPrintDocumentAdapter("CPV-Rapport"), null);
            });
        }
    }
}

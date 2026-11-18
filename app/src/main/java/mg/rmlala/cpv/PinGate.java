package mg.rmlala.cpv;

import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.graphics.Color;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.inputmethod.EditorInfo;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Native authentication gate. PIN never crosses the WebView bridge. */
final class PinGate {
    private final FragmentActivity activity;
    private final SharedPreferences prefs;
    private final KeyStore keys;
    private volatile boolean unlocked;
    private String firstPin;
    private BiometricPrompt biometric;

    PinGate(FragmentActivity activity) throws Exception {
        this.activity=activity;
        prefs=activity.getSharedPreferences("cpv-native-security-v3",0);
        keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(!keys.containsAlias("cpv-pin-pepper")){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("cpv-pin-pepper",KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).build());gen.generateKey();
        }
        if(!keys.containsAlias("cpv-vault-wrap")){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("cpv-vault-wrap",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
    }
    boolean isUnlocked(){return unlocked;}
    boolean enabled(){return prefs.getBoolean("lockEnabled",false);}
    boolean biometricEnabled(){return enabled() && prefs.getBoolean("biometricEnabled",false);}
    boolean biometricAvailable(){return BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS;}
    void configure(String action,Runnable done){
        if(!unlocked)return;
        Runnable restore=()->{unlocked=true;done.run();};
        if(action.equals("enable")){
            showPin(()->{prefs.edit().putBoolean("lockEnabled",true).apply();restore.run();},!prefs.contains("pinDigest"),false,restore);
        }else if(action.equals("disable")){
            new AlertDialog.Builder(activity).setTitle("Désactiver le verrouillage ?").setMessage("CPV s’ouvrira sans PIN ni biométrie. Vos rapports et sauvegardes seront conservés.")
                .setNegativeButton("Annuler",(d,w)->restore.run()).setOnCancelListener(d->restore.run())
                .setPositiveButton("Désactiver",(d,w)->showPin(()->{prefs.edit().putBoolean("lockEnabled",false).putBoolean("biometricEnabled",false).apply();restore.run();},false,biometricEnabled(),restore)).show();
        }else if(action.equals("change")){
            if(!enabled()){restore.run();return;}
            showPin(()->showPin(restore,true,false,restore),false,biometricEnabled(),restore);
        }else if(action.equals("biometric-on")){
            if(!enabled()||!biometricAvailable()){new AlertDialog.Builder(activity).setMessage("Activez le PIN et enregistrez une biométrie compatible dans les paramètres Android.").setPositiveButton("OK",(d,w)->restore.run()).show();return;}
            authenticateBiometric(()->{prefs.edit().putBoolean("biometricEnabled",true).apply();restore.run();},restore);
        }else if(action.equals("biometric-off")){prefs.edit().putBoolean("biometricEnabled",false).apply();restore.run();}
        else restore.run();
    }
    private void authenticateBiometric(Runnable success,Runnable cancel){
        biometric=new BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){success.run();}
            @Override public void onAuthenticationError(int code,CharSequence message){cancel.run();}
        });
        biometric.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Activer la biométrie CPV").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Annuler").build());
    }
    void lock(){unlocked=false;}
    private String pinDigest(String pin) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init((SecretKey)keys.getKey("cpv-pin-pepper",null));
        return Base64.encodeToString(mac.doFinal(("CPV3:"+pin).getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    synchronized String vaultSecret() throws Exception {
        if(!unlocked)throw new SecurityException("Application verrouillée.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");SecretKey key=(SecretKey)keys.getKey("cpv-vault-wrap",null);
        String stored=prefs.getString("vault",null);
        if(stored!=null){cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(prefs.getString("vaultIv",""),Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(stored,Base64.NO_WRAP)),StandardCharsets.UTF_8);}
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String secret=Base64.encodeToString(random,Base64.NO_WRAP);
        cipher.init(Cipher.ENCRYPT_MODE,key);String encrypted=Base64.encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs.edit().putString("vault",encrypted).putString("vaultIv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit())throw new Exception("Stockage sécurisé indisponible.");return secret;
    }
    synchronized void storeCloudToken(String token,long expires) throws Exception {
        if(!unlocked||token==null||token.length()>8192||expires<=System.currentTimeMillis()||expires>System.currentTimeMillis()+7*3600000L)throw new SecurityException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(SecretKey)keys.getKey("cpv-vault-wrap",null));cipher.updateAAD("cpv-cloud-session".getBytes(StandardCharsets.UTF_8));
        String encrypted=Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs.edit().putString("cloudToken",encrypted).putString("cloudIv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putLong("cloudExpires",expires).commit())throw new Exception();
    }
    synchronized String cloudToken() throws Exception {
        if(!unlocked||prefs.getLong("cloudExpires",0)<=System.currentTimeMillis())throw new SecurityException("Jeton expiré.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,(SecretKey)keys.getKey("cpv-vault-wrap",null),new GCMParameterSpec(128,Base64.decode(prefs.getString("cloudIv",""),Base64.NO_WRAP)));cipher.updateAAD("cpv-cloud-session".getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("cloudToken",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    synchronized void clearCloudToken(){prefs.edit().remove("cloudToken").remove("cloudIv").remove("cloudExpires").apply();}
    void show(Runnable ready){
        if(!enabled()){unlocked=true;ready.run();return;}
        showPin(ready,false,biometricEnabled(),null);
    }
    private void showPin(Runnable ready,boolean reset,boolean useBiometric,Runnable cancel){
        unlocked=false;firstPin=null;
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);
        layout.setPadding(48,48,48,48);layout.setBackgroundColor(Color.rgb(243,246,250));
        TextView title=new TextView(activity);title.setText("CPV Rapport Hebdo");title.setTextSize(25);title.setTextColor(Color.rgb(24,56,88));layout.addView(title);
        TextView note=new TextView(activity);boolean setup=reset||!prefs.contains("pinDigest");note.setText(setup?"Créez votre PIN de secours (4 chiffres).":useBiometric?"Déverrouillez par biométrie ou saisissez votre PIN.":"Saisissez votre PIN à 4 chiffres.");layout.addView(note);
        EditText pin=new EditText(activity);pin.setHint("PIN à 4 chiffres");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setSingleLine(true);pin.setImeOptions(EditorInfo.IME_ACTION_DONE);pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});layout.addView(pin,new LinearLayout.LayoutParams(-1,-2));
        Button submit=new Button(activity);submit.setAllCaps(false);submit.setText(setup?"Continuer":"Déverrouiller");layout.addView(submit);
        TextView error=new TextView(activity);error.setTextColor(Color.rgb(160,40,40));layout.addView(error);
        Runnable success=()->{unlocked=true;firstPin=null;prefs.edit().putInt("failures",0).remove("blockedUntil").apply();ready.run();};
        submit.setOnClickListener(v->{
            try{
                long blocked=prefs.getLong("blockedUntil",0)-System.currentTimeMillis();if(blocked>0){error.setText("Réessayez dans "+((blocked+999)/1000)+" secondes.");return;}
                String value=pin.getText().toString();pin.setText("");if(!value.matches("[0-9]{4}")){error.setText("Saisissez exactement 4 chiffres.");return;}
                if(setup){
                    if(firstPin==null){firstPin=value;note.setText("Confirmez le PIN de secours.");return;}
                    if(!firstPin.equals(value)){firstPin=null;note.setText("Créez votre PIN de secours.");error.setText("Les deux codes sont différents.");return;}
                    if(!prefs.edit().putString("pinDigest",pinDigest(value)).commit())throw new Exception();success.run();
                }else if(MessageDigest.isEqual(pinDigest(value).getBytes(StandardCharsets.UTF_8),prefs.getString("pinDigest","").getBytes(StandardCharsets.UTF_8)))success.run();
                else{int failures=prefs.getInt("failures",0)+1;long delay=failures>=5?Math.min(3600000L,30000L*(1L<<Math.min(7,failures-5))):0;
                    prefs.edit().putInt("failures",failures).putLong("blockedUntil",System.currentTimeMillis()+delay).apply();error.setText(delay>0?"Trop de tentatives. Réessayez dans "+delay/1000+" secondes.":"PIN incorrect.");}
            }catch(Exception e){error.setText("Le stockage sécurisé est indisponible. Aucune donnée n’a été effacée.");}
        });
        pin.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit.performClick();return true;}return false;});
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(layout,new ScrollView.LayoutParams(-1,-2));activity.setContentView(scroll);
        if(cancel!=null){Button back=new Button(activity);back.setAllCaps(false);back.setText("Annuler");layout.addView(back);back.setOnClickListener(v->{firstPin=null;cancel.run();});}
        if(!setup && useBiometric){
            try{
                if(BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS){
                    biometric=new BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),new BiometricPrompt.AuthenticationCallback(){
                        @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){super.onAuthenticationSucceeded(result);success.run();}
                        @Override public void onAuthenticationError(int code,CharSequence text){super.onAuthenticationError(code,text);note.setText("Saisissez votre PIN de secours.");}
                        @Override public void onAuthenticationFailed(){error.setText("Biométrie non reconnue. Réessayez ou utilisez le PIN.");}
                    });
                    Button retry=new Button(activity);retry.setText("Utiliser la biométrie");layout.addView(retry);
                    Runnable request=()->{try{biometric.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Déverrouiller CPV")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Utiliser le PIN").build());}catch(Exception e){note.setText("Biométrie indisponible. Utilisez votre PIN.");}};
                    retry.setOnClickListener(v->request.run());activity.getWindow().getDecorView().post(request);
                }
            }catch(Exception e){note.setText("Biométrie indisponible. Utilisez votre PIN.");}
        }
    }
}

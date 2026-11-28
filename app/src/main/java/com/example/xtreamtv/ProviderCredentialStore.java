package com.example.xtreamtv;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local password protection, available on all supported API levels. Never logs secrets. */
final class ProviderCredentialStore {
 private static final String ALIAS="sky_shark_provider_aes_v1", SEALED="provider_pass_v1", IV="provider_pass_iv_v1";
 private static SecretKey key(boolean create)throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  java.security.Key existing=store.getKey(ALIAS,null);
  if(existing instanceof SecretKey)return (SecretKey)existing;
  if(!create)throw new java.security.KeyStoreException("Key unavailable");
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setRandomizedEncryptionRequired(true).build());
  return generator.generateKey();
 }
 private static boolean seal(SharedPreferences.Editor edit,String password){
  try{
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,key(true));
   byte[] encrypted=cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
   edit.putString(SEALED,Base64.encodeToString(encrypted,Base64.NO_WRAP))
    .putString(IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).remove("pass");
   return true;
  }catch(Exception e){android.util.Log.w("SharkCredentials","Keystore encryption unavailable");return false;}
 }
 static String load(SharedPreferences prefs){
  String sealed=prefs.getString(SEALED,null),iv=prefs.getString(IV,null);
  if(sealed!=null&&iv!=null){
   try{
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
    return new String(cipher.doFinal(Base64.decode(sealed,Base64.NO_WRAP)),StandardCharsets.UTF_8);
   }catch(Exception e){android.util.Log.w("SharkCredentials","Saved password unavailable; sign in again");return "";}
  }
  String legacy=prefs.getString("pass","");
  if(!legacy.isEmpty()){
   SharedPreferences.Editor edit=prefs.edit();
   // Keep the legacy value if migration fails; do not lock existing users out.
   if(seal(edit,legacy))edit.apply();
  }
  return legacy;
 }
 static boolean save(SharedPreferences prefs,String server,String user,String password,String zone){
  SharedPreferences.Editor edit=prefs.edit().putString("server",server).putString("user",user).putString("epg_zone",zone);
  if(seal(edit,password)){edit.apply();return true;}
  // Authentication succeeded, but this device cannot persist the secret safely.
  // Keep credentials in memory only; avoid restoring stale credentials on next launch.
  prefs.edit().remove("server").remove("user").remove("pass").remove(SEALED).remove(IV).remove("epg_zone").apply();
  return false;
 }
}

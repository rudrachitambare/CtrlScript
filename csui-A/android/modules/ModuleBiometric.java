package com.ctrlscript.csua;

import android.security.keystore.*;

import androidx.biometric.*;
import androidx.fragment.app.FragmentActivity;

import java.security.*;
import javax.crypto.*;

public class ModuleBiometric implements CsuaModule {

    private final Bootstrap _ctx;

    public ModuleBiometric(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "available": return _available();
            case "verify":    _verify(s(args[0]), s(args[1])); return null;
            // Keystore
            case "save":   _ksSave(s(args[0]), s(args[1])); return null;
            case "get":    return _ksGet(s(args[0]));
            case "delete": _ksDelete(s(args[0])); return null;
        }
        return null;
    }

    private String _available() {
        BiometricManager bm = BiometricManager.from(_ctx);
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (can == BiometricManager.BIOMETRIC_SUCCESS) return "fingerprint";
        can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (can == BiometricManager.BIOMETRIC_SUCCESS) return "face";
        return "none";
    }

    private void _verify(String cbId, String reason) {
        if (!(_ctx instanceof FragmentActivity)) {
            _ctx.fireCallback(cbId, "requires_fragment_activity", null); return;
        }
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle(reason)
            .setNegativeButtonText("Cancel")
            .build();

        _ctx._ui.post(() -> {
            BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) _ctx,
                _ctx.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        _ctx.fireCallback(cbId, null, "true");
                    }
                    public void onAuthenticationError(int code, CharSequence msg) {
                        _ctx.fireCallback(cbId, msg.toString(), null);
                    }
                    public void onAuthenticationFailed() {
                        _ctx.fireCallback(cbId, "failed", null);
                    }
                });
            prompt.authenticate(info);
        });
    }

    // ── Android Keystore (encrypted secure storage) ──────

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS    = "csua_ks";

    private SecretKey _getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private void _ksSave(String key, String value) {
        try {
            SecretKey sk = _getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, sk);
            byte[] iv         = cipher.getIV();
            byte[] encrypted  = cipher.doFinal(value.getBytes("UTF-8"));
            // Store as base64(iv):base64(encrypted) in SharedPreferences
            String stored = android.util.Base64.encodeToString(iv, 0) + ":" +
                            android.util.Base64.encodeToString(encrypted, 0);
            _ctx.getSharedPreferences("csua_ks", android.content.Context.MODE_PRIVATE)
                .edit().putString(key, stored).apply();
        } catch (Exception e) { android.util.Log.e("CSUA", "Keystore save error", e); }
    }

    private String _ksGet(String key) {
        try {
            String stored = _ctx.getSharedPreferences("csua_ks", android.content.Context.MODE_PRIVATE)
                .getString(key, null);
            if (stored == null) return null;
            String[] parts    = stored.split(":");
            byte[] iv         = android.util.Base64.decode(parts[0], 0);
            byte[] encrypted  = android.util.Base64.decode(parts[1], 0);
            SecretKey sk      = _getOrCreateKey();
            Cipher cipher     = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, sk, new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), "UTF-8");
        } catch (Exception e) { return null; }
    }

    private void _ksDelete(String key) {
        _ctx.getSharedPreferences("csua_ks", android.content.Context.MODE_PRIVATE)
            .edit().remove(key).apply();
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}

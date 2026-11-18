package pe.gabrec.ucvmarcador;

import android.content.Context;
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

/**
 * Usuario y contraseña de UCV Talent guardados en la app. La contraseña se cifra con
 * una llave del Android Keystore (AES-GCM) que no sale del teléfono.
 */
public final class Credenciales {
    private static final String ALIAS = "ucvmarcador_credenciales";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";

    private Credenciales() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("credenciales", Context.MODE_PRIVATE);
    }

    private static SecretKey llave() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    /** Guarda las credenciales. Devuelve false si el teléfono no pudo cifrarlas. */
    public static boolean guardar(Context c, String usuario, String clave) {
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, llave());
            byte[] datos = cifrador.doFinal(clave.getBytes(StandardCharsets.UTF_8));
            prefs(c).edit()
                    .putString("usuario", usuario)
                    .putString("clave", Base64.encodeToString(datos, Base64.NO_WRAP))
                    .putString("iv", Base64.encodeToString(cifrador.getIV(), Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void borrar(Context c) {
        prefs(c).edit().clear().apply();
    }

    public static boolean hay(Context c) {
        return !usuario(c).isEmpty() && prefs(c).contains("clave");
    }

    public static String usuario(Context c) {
        return prefs(c).getString("usuario", "");
    }

    /** Devuelve la contraseña descifrada, o "" si no hay o no se pudo leer. */
    public static String clave(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String datos = p.getString("clave", null), iv = p.getString("iv", null);
            if (datos == null || iv == null) return "";
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.DECRYPT_MODE, llave(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cifrador.doFinal(Base64.decode(datos, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}

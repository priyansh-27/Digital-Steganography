package steg;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * AESUtil - AES-GCM encryption with PBKDF2 key derivation.
 */
public class AESUtil {

    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int ITERATIONS = 65536;
    private static final int KEY_LEN_BITS = 256;
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] encrypt(byte[] plain, String password) throws Exception {
        if (password == null || password.isEmpty())
            throw new IllegalArgumentException("Password required");

        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);

        SecretKeySpec key = deriveKey(password.toCharArray(), salt);

        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] cipherBytes = cipher.doFinal(plain);

        byte[] out = new byte[SALT_LEN + IV_LEN + cipherBytes.length];
        System.arraycopy(salt, 0, out, 0, SALT_LEN);
        System.arraycopy(iv, 0, out, SALT_LEN, IV_LEN);
        System.arraycopy(cipherBytes, 0, out, SALT_LEN + IV_LEN, cipherBytes.length);

        Arrays.fill(key.getEncoded(), (byte) 0);
        return out;
    }

    public static byte[] decrypt(byte[] combined, String password) throws Exception {
        if (password == null || password.isEmpty())
            throw new IllegalArgumentException("Password required");

        if (combined.length < (SALT_LEN + IV_LEN + 16))
            throw new IllegalArgumentException("Ciphertext too short");

        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] cipherBytes = Arrays.copyOfRange(combined, SALT_LEN + IV_LEN, combined.length);

        SecretKeySpec key = deriveKey(password.toCharArray(), salt);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        byte[] plain = cipher.doFinal(cipherBytes);

        Arrays.fill(key.getEncoded(), (byte) 0);
        return plain;
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGO);
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN_BITS);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
        return key;
    }
}

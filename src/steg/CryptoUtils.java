package steg;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * CryptoUtils
 * - Provides AES-256 encryption using PBKDF2WithHmacSHA256 for key derivation.
 * - Returns salt and IV so they can be stored in the stego header.
 */
public class CryptoUtils {

    public static final int SALT_LEN = 16;
    public static final int IV_LEN = 16;
    public static final int ITERATIONS = 65536;
    public static final int KEY_LEN = 256; // bits

    public static class CryptoResult {
        public final byte[] cipher;
        public final byte[] salt;
        public final byte[] iv;

        public CryptoResult(byte[] cipher, byte[] salt, byte[] iv) {
            this.cipher = cipher;
            this.salt = salt;
            this.iv = iv;
        }
    }

    public static CryptoResult encrypt(byte[] plain, String password) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        rnd.nextBytes(salt);
        SecretKeySpec key = deriveKey(password.toCharArray(), salt);

        // IV
        byte[] iv = new byte[IV_LEN];
        rnd.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] cipherBytes = cipher.doFinal(plain);

        return new CryptoResult(cipherBytes, salt, iv);
    }

    public static byte[] decrypt(byte[] cipherBytes, String password, byte[] salt, byte[] iv) throws Exception {
        SecretKeySpec key = deriveKey(password.toCharArray(), salt);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] plain = cipher.doFinal(cipherBytes);
        return plain;
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // PBKDF2 with HmacSHA256
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN);
        SecretKey tmp = skf.generateSecret(spec);
        byte[] keyBytes = tmp.getEncoded();
        SecretKeySpec secret = new SecretKeySpec(keyBytes, "AES");
        // Clear sensitive arrays
        Arrays.fill(keyBytes, (byte) 0);
        return secret;
    }
}

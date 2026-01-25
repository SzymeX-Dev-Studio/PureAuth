package com.pureauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class TOTP {

    private static final int PASS_CODE_LENGTH = 6;
    private static final int INTERVAL = 30;
    
    // Base32 Alphabet
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] DECODE_TABLE;

    static {
        DECODE_TABLE = new int[128];
        Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE_TABLE[ALPHABET[i]] = i;
        }
        // Handle lowercase
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] >= 'A' && ALPHABET[i] <= 'Z')
                DECODE_TABLE[ALPHABET[i] + 32] = i;
        }
    }

    public static String generateSecretKey() {
        StringBuilder sb = new StringBuilder(16);
        Random random = new Random();
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET[random.nextInt(32)]);
        }
        return sb.toString();
    }

    public static boolean validate(String secret, String codeInput) {
        if (secret == null || codeInput == null || !codeInput.matches("\\d+")) return false;
        int code = Integer.parseInt(codeInput);
        long currentInterval = System.currentTimeMillis() / 1000 / INTERVAL;
        
        for (int i = -1; i <= 1; i++) {
            if (generateTOTP(secret, currentInterval + i) == code) {
                return true;
            }
        }
        return false;
    }

    private static int generateTOTP(String secret, long interval) {
        byte[] key = decodeBase32(secret);
        byte[] data = new byte[8];
        long value = interval;
        for (int i = 8; i-- > 0; value >>>= 8) {
            data[i] = (byte) value;
        }

        try {
            SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            long truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash = (truncatedHash << 8) | (hash[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            truncatedHash %= 1000000;
            return (int) truncatedHash;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static byte[] decodeBase32(String secret) {
        // Remove separators/spaces
        secret = secret.trim().replace(" ", "").replace("-", "");
        int count = 0;
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;
        byte[] result = new byte[secret.length() * 5 / 8];
        
        for (char c : secret.toCharArray()) {
            if (c >= 128 || DECODE_TABLE[c] == -1) continue;
            buffer = (buffer << 5) | DECODE_TABLE[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[next++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}

package com.rxw.panconnection.service.wifi;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.util.Log;
import com.rxw.panconnection.service.wifi.WifiConnectionConstants;

public class WifiSHA256Generator {
    private static final String TAG = "WifiSHA256Generator";

    /*=================================================================================================*
     * Generates a SHA-256 hash from the provided passphrase                                           *
     * OriginalResult: a933 08c1 7f9a 95c7 e599 ab01 e329 4367 cd4d 3731 b40d e4e7 514b 9401 5dd8 5f56 *
     * CurrentResult: a933 08c1 7f9a 95c7 e599                                                         *
     *=================================================================================================*/
    public static String generateSHA256Hash(String Passphrase) {
        try {
            // Get a MessageDigest instance for SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Compute the hash of the passphrase bytes
            byte[] hash = digest.digest(Passphrase.getBytes("UTF-8"));
            // Convert the byte array into a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                // Convert each byte to a two-digit hexadecimal number
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Return the first 20 characters of the resulting hexadecimal string
            Log.d(TAG, "OriginalResult, Passphrase: " + hexString.toString());
            String NewPassphrase = hexString.toString().substring(0, WifiConnectionConstants.SHA256HashLength);
            Log.d(TAG, "CurrentResult, Passphrase: " + NewPassphrase);
            return NewPassphrase;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // Throw a runtime exception if an error occurs
            throw new RuntimeException(e);
        }
    }
}
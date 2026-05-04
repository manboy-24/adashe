package com.tontine.util;
import java.security.SecureRandom;
public class OtpUtil {
    private static final SecureRandom random = new SecureRandom();
    public static String generer(int longueur) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < longueur; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
package com.pureauth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static Component format(String message) {
        if (message == null) return Component.empty();
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        String result = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', buffer.toString());
        return LegacyComponentSerializer.legacySection().deserialize(result);
    }

    public static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(10, password.toCharArray());
    }

    public static boolean checkPassword(String candidate, String hashed) {
        if (hashed == null) return false;
        BCrypt.Result result = BCrypt.verifyer().verify(candidate.toCharArray(), hashed);
        return result.verified;
    }
}
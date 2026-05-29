package com.shiliuai.service.bot;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class Base64SecretEncryptor implements SecretEncryptor {
    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(encryptedText), StandardCharsets.UTF_8);
    }
}

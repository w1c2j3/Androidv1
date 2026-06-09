package com.shiliuai.service.bot;

public interface SecretEncryptor {
    String encrypt(String plainText);

    String decrypt(String encryptedText);
}

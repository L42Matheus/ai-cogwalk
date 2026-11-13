package br.edu.ifpb.aicogwalk.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private Hashing() {}

    public static String sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                md.update(p);
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String... parts) {
        byte[][] b = new byte[parts.length][];
        for (int i = 0; i < parts.length; i++) b[i] = (parts[i] == null ? "" : parts[i]).getBytes(StandardCharsets.UTF_8);
        return sha256(b);
    }
}

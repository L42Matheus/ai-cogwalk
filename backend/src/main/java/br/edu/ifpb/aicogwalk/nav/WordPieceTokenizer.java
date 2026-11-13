package br.edu.ifpb.aicogwalk.nav;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokenizador WordPiece no padrão BERT "uncased" (minúsculas, sem acentos), equivalente ao usado
 * pelo all-MiniLM-L6-v2. Implementado sem bibliotecas nativas para rodar em qualquer contêiner.
 */
public class WordPieceTokenizer {
    private final Map<String, Integer> vocab = new HashMap<>();
    private final int unk, cls, sep;
    private final int maxLength;

    public WordPieceTokenizer(Path vocabFile, int maxLength) {
        try {
            List<String> lines = Files.readAllLines(vocabFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) vocab.put(lines.get(i).strip(), i);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o vocabulário: " + vocabFile, e);
        }
        this.unk = id("[UNK]");
        this.cls = id("[CLS]");
        this.sep = id("[SEP]");
        this.maxLength = maxLength;
    }

    private int id(String token) {
        Integer v = vocab.get(token);
        if (v == null) throw new IllegalStateException("Token ausente no vocabulário: " + token);
        return v;
    }

    /** Ids com [CLS] ... [SEP], truncados em {@code maxLength}. */
    public long[] encode(String text) {
        List<Integer> ids = new ArrayList<>();
        ids.add(cls);
        for (String word : basicTokens(text)) {
            for (int piece : wordPiece(word)) {
                if (ids.size() >= maxLength - 1) break;
                ids.add(piece);
            }
        }
        ids.add(sep);
        long[] out = new long[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    static List<String> basicTokens(String text) {
        String t = Normalizer.normalize(text == null ? "" : text.toLowerCase(), Normalizer.Form.NFD);
        StringBuilder clean = new StringBuilder();
        t.codePoints().forEach(cp -> {
            if (cp == 0 || cp == 0xFFFD || Character.getType(cp) == Character.NON_SPACING_MARK) return;
            if (Character.isISOControl(cp) && !Character.isWhitespace(cp)) return;
            if (isCjk(cp)) {
                clean.append(' ').appendCodePoint(cp).append(' ');
            } else {
                clean.appendCodePoint(Character.isWhitespace(cp) ? ' ' : cp);
            }
        });
        List<String> out = new ArrayList<>();
        for (String w : clean.toString().trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            StringBuilder cur = new StringBuilder();
            w.codePoints().forEach(cp -> {
                if (isPunctuation(cp)) {
                    if (!cur.isEmpty()) { out.add(cur.toString()); cur.setLength(0); }
                    out.add(new String(Character.toChars(cp)));
                } else {
                    cur.appendCodePoint(cp);
                }
            });
            if (!cur.isEmpty()) out.add(cur.toString());
        }
        return out;
    }

    private List<Integer> wordPiece(String word) {
        if (word.codePointCount(0, word.length()) > 100) return List.of(unk);
        List<Integer> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            Integer found = null;
            while (start < end) {
                String sub = (start > 0 ? "##" : "") + word.substring(start, end);
                Integer v = vocab.get(sub);
                if (v != null) { found = v; break; }
                end = word.offsetByCodePoints(end, -1);
            }
            if (found == null) return List.of(unk);
            pieces.add(found);
            start = end;
        }
        return pieces;
    }

    static boolean isPunctuation(int cp) {
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0x20000 && cp <= 0x2A6DF)
                || (cp >= 0x2A700 && cp <= 0x2B73F) || (cp >= 0x2B740 && cp <= 0x2B81F) || (cp >= 0x2B820 && cp <= 0x2CEAF)
                || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0x2F800 && cp <= 0x2FA1F);
    }
}

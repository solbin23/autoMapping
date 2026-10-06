package com.example.aimapper.matching.domain.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 규칙 구현체들이 공유하는 문자열 정규화, 편집 거리 및 토큰 중복 계산 유틸리티이다. */
final class TextSimilarity {
    private TextSimilarity() {}

    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static double compare(String left, String right) {
        String a = normalize(left), b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 100;
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1];
            next[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                next[j] = Math.min(Math.min(next[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            previous = next;
        }
        double edit = 100.0 * (1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length()));
        return Math.max(edit, overlap(tokens(left), tokens(right)));
    }

    static Set<String> tokens(String value) {
        return Arrays.stream(value.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                        .toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank()).collect(Collectors.toSet());
    }

    static double overlap(Set<String> left, Set<String> right) {
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) return 0;
        Set<String> common = new HashSet<>(left);
        common.retainAll(right);
        return 100.0 * common.size() / union.size();
    }
}

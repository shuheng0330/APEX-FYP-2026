package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.GeneratedSopContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Spots a quiz question that asks what another module's question already asks, so a course doesn't test the same fact
 * in several modules (test note AR-03). Two questions repeat when at least half of their content words are shared;
 * a question of fewer than four content words repeats only one with exactly the same words.
 */
public final class QuestionOverlap {

    /** Share of content words two questions have in common (of all the words either uses) that makes them a repeat. */
    static final double REPEAT_SHARE = 0.5;
    private static final int MIN_WORDS = 4;
    private static final Set<String> IGNORED = Set.of("the", "and", "for", "are", "its", "this", "that", "what", "which",
            "when", "where", "who", "why", "how", "must", "should", "shall", "can", "does", "did", "not", "any", "all",
            "each", "every", "with", "from", "into", "within", "your", "you", "their", "they", "them", "there", "was",
            "were", "has", "have", "had", "than", "then", "also", "only", "after", "before", "during", "about", "true",
            "false", "these", "following", "part");

    private QuestionOverlap() {
    }

    /** The questions of {@code quiz} that ask nothing {@code asked} already asks, in their order. */
    public static List<GeneratedSopContent.GeneratedQuestion> withoutRepeats(List<GeneratedSopContent.GeneratedQuestion> quiz,
                                                                           Collection<String> asked) {
        List<Set<String>> earlier = new ArrayList<>();
        for (String question : asked) {
            earlier.add(words(question));
        }
        List<GeneratedSopContent.GeneratedQuestion> kept = new ArrayList<>();
        for (GeneratedSopContent.GeneratedQuestion q : quiz) {
            Set<String> mine = words(q.getQuestion());
            if (earlier.stream().noneMatch(other -> repeat(mine, other))) {
                kept.add(q);
            }
        }
        return kept;
    }

    /** Whether two questions ask the same thing. */
    public static boolean repeats(String question, String other) {
        return repeat(words(question), words(other));
    }

    private static boolean repeat(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (Math.min(a.size(), b.size()) < MIN_WORDS) {
            return a.equals(b);
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        return (double) shared.size() / (a.size() + b.size() - shared.size()) >= REPEAT_SHARE;
    }

    /** The question's content words: lower case, three letters or more, common words left out. */
    static Set<String> words(String question) {
        Set<String> out = new HashSet<>();
        if (question == null) {
            return out;
        }
        for (String w : question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 2 && !IGNORED.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}

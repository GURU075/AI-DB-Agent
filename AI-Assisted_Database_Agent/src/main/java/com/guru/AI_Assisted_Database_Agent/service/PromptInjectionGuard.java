package com.guru.AI_Assisted_Database_Agent.service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Heuristic early rejection only; SQL policy remains the execution boundary. */
public final class PromptInjectionGuard {
    private PromptInjectionGuard() {}

    private static final Pattern ATTACK = Pattern.compile(
            "(?is)\\b(ignore|disregard|forget|override|bypass)\\b.{0,80}\\b(instructions?|rules?|policy|policies|safeguards?|confirmation|system\\s+prompt)\\b"
            + "|\\b(reveal|print|show|repeat|expose)\\b.{0,60}\\b(system\\s+prompt|developer\\s+(message|instructions?)|api\\s*key|credentials?|secrets?)\\b"
            + "|\\b(act\\s+as|you\\s+are\\s+now|switch\\s+to)\\b.{0,50}\\b(system|developer|unrestricted|admin|jailbreak)\\b"
            + "|<\\s*/?\\s*(system|developer|schema_metadata)\\b|\\[\\s*INST\\s*]|<\\|(?:im_start|im_end|system|endoftext)\\|>"
            + "|(?:^|\\n)\\s*(system|developer)\\s*:");

    public static boolean isSuspicious(String input) {
        if (input == null) return false;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("\\p{Cf}", "");
        return ATTACK.matcher(normalized).find();
    }
}

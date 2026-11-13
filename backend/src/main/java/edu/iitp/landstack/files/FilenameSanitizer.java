package edu.iitp.landstack.files;

import java.text.Normalizer;

/** Produces a display-safe original filename. It is metadata only and never used to build a path. */
public final class FilenameSanitizer {
    private FilenameSanitizer() {}

    public static String sanitize(String name) {
        if (name == null) return "upload";
        String n = Normalizer.normalize(name, Normalizer.Form.NFC);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) {
            if (Character.isISOControl(c) || "<>:\"|?*".indexOf(c) >= 0) continue;
            sb.append(c);
        }
        String out = sb.toString().strip();
        while (out.startsWith(".")) out = out.substring(1);
        if (out.isBlank()) out = "upload";
        if (out.length() > 200) {
            int dot = out.lastIndexOf('.');
            String ext = dot > 0 && out.length() - dot <= 10 ? out.substring(dot) : "";
            out = out.substring(0, 200 - ext.length()) + ext;
        }
        return out;
    }

    public static String extension(String sanitized) {
        int dot = sanitized.lastIndexOf('.');
        return dot < 0 ? "" : sanitized.substring(dot + 1).toLowerCase();
    }
}

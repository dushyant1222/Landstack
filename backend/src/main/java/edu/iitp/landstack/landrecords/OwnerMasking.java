package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.security.CurrentUser;
import org.springframework.stereotype.Component;

/** Data minimisation: read-only reviewers see owner names as initials ("R**** K****") by default. */
@Component
public class OwnerMasking {
    private final boolean maskForReviewer;

    public OwnerMasking(LandStackProperties props) {
        this.maskForReviewer = props.privacy().maskOwnerForReviewer();
    }

    public boolean active() {
        return maskForReviewer && CurrentUser.isReviewer();
    }

    public String apply(String owner) {
        return active() ? mask(owner) : owner;
    }

    public static String mask(String owner) {
        if (owner == null || owner.isBlank()) return owner;
        StringBuilder sb = new StringBuilder();
        for (String part : owner.strip().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.appendCodePoint(part.codePointAt(0)).append("****");
        }
        return sb.toString();
    }
}

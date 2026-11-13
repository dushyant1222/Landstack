package edu.iitp.landstack.users;

import edu.iitp.landstack.common.ApiException;

/** Minimal password policy: length 10-128 with at least one letter and one digit. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void check(String field, String password) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw ApiException.field(field, "must be 10-128 characters");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw ApiException.field(field, "must contain at least one letter and one digit");
        }
    }
}

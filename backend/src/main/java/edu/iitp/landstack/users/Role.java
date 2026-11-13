package edu.iitp.landstack.users;

public enum Role {
    ADMINISTRATOR,
    DATA_OPERATOR,
    READ_ONLY_REVIEWER;

    public String authority() {
        return "ROLE_" + name();
    }
}

package ym.getout.model;

public enum PunishmentType {
    BAN,
    TEMPBAN,
    KICK;

    public static PunishmentType fromString(String type) {
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BAN;
        }
    }
}

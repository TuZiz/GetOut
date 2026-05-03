package ym.getout.storage;

import ym.getout.model.BanRecord;

import java.util.UUID;

public interface BanStore {
    long createBan(UUID uuid, String name, String reason, UUID operatorUuid, String operatorName, Long expiresAt, String serverId);

    BanRecord findActiveBan(UUID uuid);

    void deactivateBan(UUID uuid);

    int deactivateExpiredBans();
}

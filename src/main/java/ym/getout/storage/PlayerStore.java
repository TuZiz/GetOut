package ym.getout.storage;

import ym.getout.model.PlayerIndex;

import java.util.UUID;

public interface PlayerStore {
    void upsert(UUID uuid, String name, String serverId);

    default void upsert(UUID uuid, String name, String serverId, String lastIp) {
        upsert(uuid, name, serverId);
    }

    PlayerIndex findByUuid(UUID uuid);

    PlayerIndex findByName(String name);

    PlayerIndex findByNameOrUuid(String input);
}

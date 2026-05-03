package ym.getout.storage;

import ym.getout.model.PlayerIndex;

import java.util.UUID;

public interface PlayerStore {
    void upsert(UUID uuid, String name, String serverId);

    PlayerIndex findByUuid(UUID uuid);

    PlayerIndex findByName(String name);

    PlayerIndex findByNameOrUuid(String input);
}

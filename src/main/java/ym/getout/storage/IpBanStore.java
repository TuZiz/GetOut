package ym.getout.storage;

import ym.getout.model.IpBanRecord;

import java.util.UUID;

public interface IpBanStore {
    long createIpBan(String ip, String reason, UUID operatorUuid, String operatorName, String serverId);

    IpBanRecord findActiveIpBan(String ip);

    void deactivateIpBan(String ip);
}

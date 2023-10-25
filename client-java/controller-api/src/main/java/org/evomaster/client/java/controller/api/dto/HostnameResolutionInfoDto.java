package org.evomaster.client.java.controller.api.dto;

import java.util.Objects;

public class HostnameResolutionInfoDto {

    public String remoteHostname;

    public Boolean resolved;

    public HostnameResolutionInfoDto(){};

    public HostnameResolutionInfoDto(String remoteHostname, Boolean resolved) {
        this.remoteHostname = remoteHostname;
        this.resolved = resolved;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostnameResolutionInfoDto that = (HostnameResolutionInfoDto) o;
        return Objects.equals(remoteHostname, that.remoteHostname) && Objects.equals(resolved, that.resolved);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remoteHostname, resolved);
    }
}
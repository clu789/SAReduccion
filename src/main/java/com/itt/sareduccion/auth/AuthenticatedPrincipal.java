package com.itt.sareduccion.auth;

public final class AuthenticatedPrincipal implements SessionPrincipal {

    private final String oracleUsername;
    private final String profileId;
    private final AuthRole role;

    public AuthenticatedPrincipal(String oracleUsername, String profileId, AuthRole role) {
        this.oracleUsername = oracleUsername;
        this.profileId = profileId;
        this.role = role;
    }

    public String getOracleUsername() {
        return oracleUsername;
    }

    public String getProfileId() {
        return profileId;
    }

    @Override
    public AuthRole getRole() {
        return role;
    }

    @Override
    public String getUsername() {
        return oracleUsername;
    }

    @Override
    public String getIdentity() {
        return profileId;
    }

    @Override
    public Integer getPeriodo() {
        return null;
    }

    @Override
    public Integer getAnio() {
        return null;
    }
}
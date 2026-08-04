package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.Role;
import org.springframework.stereotype.Component;

@Component("accessPolicy")
public class AccessPolicy {
    private final CurrentUser currentUser;

    public AccessPolicy(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public boolean isAdmin() {
        return currentUser.require().getRole() == Role.ADMIN;
    }
}

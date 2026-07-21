package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }
    public User require() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw ApiException.unauthorized("请先登录"); return users.findByUsername(auth.getName()).orElseThrow(() -> ApiException.unauthorized("账号不存在")); }
}

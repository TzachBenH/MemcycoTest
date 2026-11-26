package com.memcyco.scheduler.security;

import org.springframework.stereotype.Component;

/** Placeholder until authentication is implemented: every request is made by "anonymous". */
@Component
public class AnonymousCurrentUserProvider implements CurrentUserProvider {

    public static final String ANONYMOUS = "anonymous";

    @Override
    public String currentUsername() {
        return ANONYMOUS;
    }
}

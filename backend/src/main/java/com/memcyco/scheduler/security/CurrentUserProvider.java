package com.memcyco.scheduler.security;

/**
 * Who is performing the current request.
 * <p>
 * Authentication is only <em>prepared</em> in this project: the single implementation returns "anonymous".
 * When real authentication is added, only this implementation (and {@link SecurityConfig}) need to change;
 * auditing ({@code created_by}/{@code updated_by}) and edit locks already go through this interface.
 */
public interface CurrentUserProvider {

    String currentUsername();
}

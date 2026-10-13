package com.litterboxd.app.auth;

import com.litterboxd.app.data.model.User;

/**
 * Interface defining authentication operations. Designed so a cloud implementation can be added later
 * without changing the Activities.
 */
public interface AuthRepository {
    void register(String email, String password, AuthCallback callback);
    void login(String email, String password, AuthCallback callback);
    void logout();
    User getCurrentUser();
    boolean isLoggedIn();
    void continueAsGuest();
}

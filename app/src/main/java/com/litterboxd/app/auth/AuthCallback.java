package com.litterboxd.app.auth;

import com.litterboxd.app.data.model.User;

/**
 * Callback interface for asynchronous authentication operations (register and login).
 */
public interface AuthCallback {
    void onSuccess(User user);
    void onError(String message);
}

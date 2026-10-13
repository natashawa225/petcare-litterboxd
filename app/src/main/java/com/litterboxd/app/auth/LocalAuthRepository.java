package com.litterboxd.app.auth;

import com.litterboxd.app.data.dao.UserDao;
import com.litterboxd.app.data.model.User;
import com.litterboxd.app.data.session.SessionManager;
import com.litterboxd.app.util.AppExecutors;

/**
 * Local implementation of AuthRepository using SQLite (via UserDao), PasswordHasher, and SessionManager.
 * Executes blocking database and crypto operations on background thread and delivers callbacks on main thread.
 */
public class LocalAuthRepository implements AuthRepository {
    private final UserDao userDao;
    private final SessionManager sessionManager;
    private final AppExecutors executors;

    public LocalAuthRepository(UserDao userDao, SessionManager sessionManager, AppExecutors executors) {
        this.userDao = userDao;
        this.sessionManager = sessionManager;
        this.executors = executors;
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        final String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        final String pwd = password == null ? "" : password;

        executors.diskIO().execute(() -> {
            // Check if user already exists
            User existing = userDao.getUserByEmail(normalizedEmail);
            if (existing != null) {
                executors.mainThread().execute(() ->
                        callback.onError("This email is already registered. Log in instead?")
                );
                return;
            }

            PasswordHasher.HashResult hashResult = PasswordHasher.hashPassword(pwd);
            long createdAt = System.currentTimeMillis();
            long userId = userDao.insertUser(normalizedEmail, hashResult.hash, hashResult.salt, createdAt);

            if (userId == -1) {
                executors.mainThread().execute(() ->
                        callback.onError("Registration failed. Please try again.")
                );
                return;
            }

            User newUser = new User(userId, normalizedEmail, hashResult.hash, hashResult.salt, createdAt);
            sessionManager.createLoginSession(userId);

            executors.mainThread().execute(() -> callback.onSuccess(newUser));
        });
    }

    @Override
    public void login(String email, String password, AuthCallback callback) {
        final String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        final String pwd = password == null ? "" : password;

        executors.diskIO().execute(() -> {
            User user = userDao.getUserByEmail(normalizedEmail);
            boolean isValid = false;

            if (user != null) {
                isValid = PasswordHasher.verify(pwd, user.getPasswordHash(), user.getSalt());
            }

            if (!isValid) {
                // Generic error message to prevent user enumeration attacks
                executors.mainThread().execute(() ->
                        callback.onError("Email or password is incorrect.")
                );
                return;
            }

            sessionManager.createLoginSession(user.getId());
            executors.mainThread().execute(() -> callback.onSuccess(user));
        });
    }

    @Override
    public void logout() {
        sessionManager.logout();
    }

    @Override
    public User getCurrentUser() {
        long userId = sessionManager.getUserId();
        if (userId != -1) {
            return userDao.getUserById(userId);
        }
        return null;
    }

    @Override
    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    @Override
    public void continueAsGuest() {
        sessionManager.createGuestSession();
    }
}

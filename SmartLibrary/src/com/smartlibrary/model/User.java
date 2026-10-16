package com.smartlibrary.model;

import com.smartlibrary.auth.Role;

/**
 * A SmartLibrary account as stored in users.csv.
 * <p>
 * {@link #toString()} never includes the password hash, so a User can be logged safely.
 */
public class User {

    private long userId;
    private String name;
    private String username;
    private String email;
    private String passwordHash;
    private Role role;
    private AccountStatus status;
    private boolean mustChangePassword;

    public User(long userId, String name, String username, String email, String passwordHash,
                Role role, AccountStatus status, boolean mustChangePassword) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
    }

    /** Returns an independent copy of this user. */
    public User copy() {
        return new User(userId, name, username, email, passwordHash, role, status, mustChangePassword);
    }

    /** Returns a copy with the password hash removed, suitable for handing to the UI. */
    public User withoutSecrets() {
        User c = copy();
        c.passwordHash = null;
        return c;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    @Override
    public String toString() {
        return "User{id=" + userId + ", username=" + username + ", role=" + role + ", status=" + status + "}";
    }
}

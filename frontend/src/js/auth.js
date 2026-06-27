/**
 * Secure Authentication Module
 * 
 * SECURITY PATTERN:
 * 1. The Access Token (JWT) is stored ONLY in memory (variable closure).
 * 2. It is NEVER stored in localStorage, sessionStorage, or IndexedDB to prevent XSS exfiltration.
 * 3. The Refresh Token is handled entirely by the browser via HttpOnly, Secure, SameSite=Strict cookies.
 *    The JavaScript application NEVER sees the Refresh Token.
 */
const auth = (function() {
    
    // In-memory token storage (lost on page reload, requiring silent refresh)
    let accessToken = null;
    let currentUser = null;
    let tokenExpiryTime = null;

    const API_BASE = window.APP_AUTH_API_BASE || 'http://localhost:8084/api/v1/auth';

    /**
     * Perform login and set tokens.
     */
    async function login(email, password) {
        try {
            const response = await fetch(`${API_BASE}/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                // credentials: 'include' is CRITICAL so the browser accepts the Set-Cookie for the Refresh Token
                credentials: 'include', 
                body: JSON.stringify({ email, password })
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.error?.message || 'Login failed');
            }

            // Store token purely in JS memory
            accessToken = result.data.accessToken;
            currentUser = result.data.user;
            
            // Calculate expiry time (buffer of 60 seconds)
            tokenExpiryTime = new Date().getTime() + (result.data.expiresIn * 1000) - 60000;

            console.log("Login successful. Access token stored in memory.");
            return true;
            
        } catch (error) {
            console.error("Login attempt failed:", error);
            throw error;
        }
    }

    /**
     * Silent Refresh
     * Automatically attempts to get a new access token using the HttpOnly cookie.
     */
    async function silentRefresh() {
        try {
            const response = await fetch(`${API_BASE}/refresh`, {
                method: 'POST',
                // credentials: 'include' is CRITICAL so browser sends the HttpOnly refresh token cookie
                credentials: 'include' 
            });

            if (!response.ok) {
                // Refresh failed (cookie expired, or logged out)
                accessToken = null;
                currentUser = null;
                return false;
            }

            const result = await response.json();
            
            accessToken = result.data.accessToken;
            currentUser = result.data.user;
            tokenExpiryTime = new Date().getTime() + (result.data.expiresIn * 1000) - 60000;

            return true;
        } catch (error) {
            console.warn("Silent refresh failed", error);
            return false;
        }
    }

    /**
     * Logout
     */
    async function logout() {
        try {
            await fetch(`${API_BASE}/logout`, {
                method: 'POST',
                credentials: 'include'
            });
        } catch (e) {
            console.warn("Logout network issue, dropping local state anyway.");
        } finally {
            accessToken = null;
            currentUser = null;
            window.location.href = 'login.html?force=1';
        }
    }

    /**
     * Fetch wrapper that automatically attaches the access token
     * and handles 401s by attempting a silent refresh.
     */
    async function fetchWithAuth(url, options = {}) {
        // Check if token needs refresh
        if (!accessToken || new Date().getTime() > tokenExpiryTime) {
            const refreshed = await silentRefresh();
            if (!refreshed) {
                throw new Error("Authentication required");
            }
        }

        const headers = new Headers(options.headers || {});
        headers.set('Authorization', `Bearer ${accessToken}`);

        const authOptions = {
            ...options,
            headers
        };

        let response = await fetch(url, authOptions);

        // If the server rejected the token despite our expiry check
        if (response.status === 401) {
            console.log("Token rejected by server, attempting refresh...");
            const refreshed = await silentRefresh();
            if (refreshed) {
                // Retry requested fetch
                headers.set('Authorization', `Bearer ${accessToken}`);
                response = await fetch(url, authOptions);
            }
        }

        return response;
    }

    return {
        login,
        logout,
        silentRefresh,
        fetchWithAuth,
        getUser: () => currentUser,
        getAccessToken: () => accessToken,
        isAuthenticated: () => !!accessToken
    };
})();

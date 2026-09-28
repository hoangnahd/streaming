// ---------------------------------------------------------------------
// Shared client-side auth guard for static pages served separately from
// the Spring Boot backend (e.g. this dev server on :5500 vs backend on
// :8080). This only gates the UI — hides content and redirects — it is
// NOT the real security boundary. That boundary is Spring Security on
// every /api/** call, which enforces this regardless of what this script
// does. Include this file BEFORE each page's own <script> block.
// ---------------------------------------------------------------------

const AUTH_API_BASE = 'http://localhost:8080';
const LOGIN_URL = `${window.location.origin}/index.html`;

/**
 * Calls GET /api/me. Redirects to LOGIN_URL if there's no valid session,
 * or to `forbiddenRedirect` if the user is logged in but their role isn't
 * `requiredRole`. Resolves to { username, role } only when the check
 * passes; otherwise resolves to null (the caller should just return —
 * a redirect is already underway).
 */
async function requireAuth(requiredRole, forbiddenRedirect) {
    try {
        const res = await fetch(AUTH_API_BASE + '/api/me', { credentials: 'include' });
        if (!res.ok) {
            window.location.href = LOGIN_URL;
            return null;
        }
        const user = await res.json();
        if (requiredRole && user.role !== requiredRole) {
            window.location.href = forbiddenRedirect || LOGIN_URL;
            return null;
        }
        document.body.style.visibility = 'visible';
        return user;
    } catch (err) {
        console.error('Auth check failed:', err);
        window.location.href = LOGIN_URL;
        return null;
    }
}

/** Fills in the header's user badge from the /api/me response. */
function applyUserToHeader(user) {
    if (!user || !user.username) return;
    const initialEl = document.getElementById('userInitial');
    const nameEl = document.getElementById('userDisplayName');
    if (initialEl) initialEl.textContent = user.username.charAt(0).toUpperCase();
    if (nameEl) nameEl.textContent = user.username;
}
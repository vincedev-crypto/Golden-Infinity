/**
 * Main application logic
 */

document.addEventListener('DOMContentLoaded', async () => {
    // Attempt silent authentication on load (checks if user has a valid refresh cookie session)
    const isAuthenticated = await auth.silentRefresh();
    updateUI(isAuthenticated);
});

function showLogin() {
    window.location.href = 'login.html';
}

function showRegister() {
    window.location.href = 'register.html';
}

function updateUI(isAuthenticated) {
    const authControls = document.getElementById('auth-controls');
    const userControls = document.getElementById('user-controls');
    const greeting = document.getElementById('user-greeting');
    const bookNav = document.getElementById('nav-book-ticket');
    const adminNav = document.getElementById('nav-admin-appointments');

    if (!authControls || !userControls || !greeting || !bookNav || !adminNav) {
        return;
    }

    if (isAuthenticated) {
        const user = auth.getUser();
        const companyRoles = ['STAFF', 'ADMIN', 'SUPERADMIN'];
        const isCompanyUser = user && companyRoles.includes(user.role);
        
        // Remove style override to let d-flex take over
        authControls.setAttribute('style', 'display: none!important;');
        userControls.setAttribute('style', 'display: flex!important;');
        
        // Safe DOM manipulation
        greeting.textContent = `Hello, ${user.firstName}`;
        bookNav.style.display = isCompanyUser ? 'none' : 'block';
        adminNav.style.display = isCompanyUser ? 'block' : 'none';
    } else {
        authControls.setAttribute('style', 'display: flex!important;');
        userControls.setAttribute('style', 'display: none!important;');
        bookNav.style.display = 'none';
        adminNav.style.display = 'none';
        greeting.textContent = '';
    }
}

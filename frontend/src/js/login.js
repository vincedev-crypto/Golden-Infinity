document.addEventListener('DOMContentLoaded', async () => {
    const loginForm = document.getElementById('loginForm');
    const errorDiv = document.getElementById('login-error');
    const params = new URLSearchParams(window.location.search);
    const forceLogin = params.get('force') === '1';

    if (!forceLogin) {
        const isAuthenticated = await auth.silentRefresh();
        if (isAuthenticated) {
            window.location.href = getPostLoginTarget();
            return;
        }
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value;

        errorDiv.classList.add('d-none');
        errorDiv.textContent = '';

        try {
            await auth.login(email, password);
            loginForm.reset();
            window.location.href = getPostLoginTarget();
        } catch (err) {
            errorDiv.textContent = err.message || 'Login failed';
            errorDiv.classList.remove('d-none');
        }
    });

    function getPostLoginTarget() {
        const next = params.get('next');
        if (next) {
            return next;
        }

        const user = auth.getUser();
        const companyRoles = ['STAFF', 'ADMIN', 'SUPERADMIN'];
        return user && companyRoles.includes(user.role) ? 'admin-appointments.html' : 'index.html';
    }
});

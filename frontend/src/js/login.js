document.addEventListener('DOMContentLoaded', async () => {
    const loginForm = document.getElementById('loginForm');
    const errorDiv = document.getElementById('login-error');
    const params = new URLSearchParams(window.location.search);
    const forceLogin = params.get('force') === '1';

    if (!forceLogin) {
        const isAuthenticated = await auth.silentRefresh();
        if (isAuthenticated) {
            window.location.href = 'index.html';
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
            window.location.href = 'index.html';
        } catch (err) {
            errorDiv.textContent = err.message || 'Login failed';
            errorDiv.classList.remove('d-none');
        }
    });
});

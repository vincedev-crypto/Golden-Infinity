document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('registerForm');
    const message = document.getElementById('register-message');

    if (!form || !message) {
        return;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        setMessage('', '');

        const payload = {
            firstName: document.getElementById('firstName').value.trim(),
            lastName: document.getElementById('lastName').value.trim(),
            email: document.getElementById('registerEmail').value.trim(),
            mobileNo: document.getElementById('mobileNo').value.trim(),
            password: document.getElementById('registerPassword').value
        };

        try {
            const response = await fetch(window.APP_REGISTER_API_BASE || 'http://localhost:8084/api/v1/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const result = await response.json();
            if (!response.ok) {
                const details = result.error?.details;
                const detailText = details ? Object.values(details).join(' ') : '';
                throw new Error(detailText || result.error?.message || result.message || 'Registration failed');
            }

            form.reset();
            setMessage('Registration successful. Redirecting to login...', 'success');
            setTimeout(() => {
                window.location.href = 'login.html';
            }, 1000);
        } catch (error) {
            setMessage(error.message || 'Registration failed', 'danger');
        }
    });

    function setMessage(text, variant) {
        message.textContent = text;
        message.className = 'alert d-none';
        if (text && variant) {
            message.classList.remove('d-none');
            message.classList.add(`alert-${variant}`);
        }
    }
});

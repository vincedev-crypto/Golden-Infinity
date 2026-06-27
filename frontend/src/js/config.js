(function() {
    const localApi = 'http://localhost:8084';
    const productionApi = 'https://api.goldeninfinity.com';
    const apiOrigin = ['localhost', '127.0.0.1'].includes(window.location.hostname)
        ? localApi
        : productionApi;

    window.APP_AUTH_API_BASE = `${apiOrigin}/api/v1/auth`;
    window.APP_REGISTER_API_BASE = `${apiOrigin}/api/v1/auth/register`;
    window.APP_APPOINTMENT_API_BASE = `${apiOrigin}/api/v1/appointments`;
    window.APP_APPOINTMENT_WS_BASE = `${apiOrigin.replace(/^http/, 'ws')}/api/v1/ws/appointments`;
})();

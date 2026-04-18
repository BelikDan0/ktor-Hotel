// Функция для проверки: если мы на странице логина/регистрации и есть токен — уходим на главную
window.onload = async () => {
    const token = localStorage.getItem('token');
    const isAuthPage = window.location.pathname.includes('login') || window.location.pathname.includes('register');

    // Если мы на странице Входа/Регистрации И у нас есть токен
    if (token && isAuthPage) {
        // Сначала ПРОВЕРЯЕМ токен на сервере, прежде чем редиректить
        const res = await fetch('http://localhost:8080/auth/verify', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (res.ok) {
            window.location.href = 'index.html'; // Только если сервер подтвердил!
        } else {
            localStorage.removeItem('token'); // Если токен тухлый — удаляем его нафиг
        }
    }

    // Если мы на Главной И токена НЕТ
    if (!token && !isAuthPage) {
        window.location.href = 'login.html';
    }
};

// Функция авторизации/регистрации
async function auth(type) {
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch(`http://localhost:8080/${type}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const result = await response.json();

        if (response.ok) {
            // Если регистрация/вход прошли успешно и бэк вернул токен
            if (result.token) {
                localStorage.setItem('token', result.token);
                window.location.href = 'index.html';
            } else if (type === 'register') {
                // Фолбэк, если бэк еще не отдает токен при регистрации
                alert('Регистрация успешна! Войдите.');
                window.location.href = 'login.html';
            }
        } else {
            alert('Ошибка: ' + (result.error || 'Доступ запрещен'));
        }
    } catch (e) {
        alert('Сервер недоступен');
    }
}
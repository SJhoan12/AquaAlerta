(function () {
    var storageKey = 'aquaalerta-theme';
    var theme = 'light';

    try {
        theme = localStorage.getItem(storageKey) === 'dark' ? 'dark' : 'light';
    } catch (error) {
        theme = 'light';
    }

    function renderTheme() {
        document.querySelectorAll('.theme-toggle').forEach(function (button) {
            var isDark = document.documentElement.dataset.theme === 'dark';
            var label = button.querySelector('.theme-label');
            var icon = button.querySelector('.theme-icon');
            button.setAttribute('aria-pressed', String(isDark));
            button.setAttribute('aria-label', isDark
                ? 'Cambiar a modo claro'
                : 'Cambiar a modo oscuro');
            if (label) label.textContent = isDark ? 'Modo claro' : 'Modo oscuro';
            if (icon) icon.textContent = isDark ? '☀' : '☾';
        });
    }

    function applyTheme(nextTheme) {
        document.documentElement.dataset.theme = nextTheme;
        document.documentElement.style.colorScheme = nextTheme;
        renderTheme();
        window.dispatchEvent(new Event('aquaalerta:themechange'));
    }

    applyTheme(theme);

    document.addEventListener('DOMContentLoaded', function () {
        renderTheme();
        document.querySelectorAll('.theme-toggle').forEach(function (button) {
            button.addEventListener('click', function () {
                var nextTheme = document.documentElement.dataset.theme === 'dark'
                    ? 'light'
                    : 'dark';
                try {
                    localStorage.setItem(storageKey, nextTheme);
                } catch (error) {
                    // The current page still changes theme if browser storage is unavailable.
                }
                applyTheme(nextTheme);
            });
        });
    });
})();

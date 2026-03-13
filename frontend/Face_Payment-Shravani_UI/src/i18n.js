function applyLanguage(lang = 'en') {
    if (typeof translations === 'undefined') {
        console.warn('translations.js not loaded yet');
        return;
    }
    const dict = translations[lang] || translations.en;
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        if (dict[key]) {
            if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                el.placeholder = dict[key];
            } else {
                el.innerText = dict[key];
            }
        }
    });

    // Explicit placeholder translations
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
        const key = el.getAttribute('data-i18n-placeholder');
        if (dict[key]) {
            el.placeholder = dict[key];
        }
    });

    // Update active lang in UI if elements exist
    const currentLangEl = document.getElementById('currentLang');
    if (currentLangEl) currentLangEl.innerText = lang.toUpperCase();

    // Update HTML lang attribute
    document.documentElement.lang = lang;
}

window.setLanguage = function(lang) {
    localStorage.setItem('billme_lang', lang);
    applyLanguage(lang);
    // Some pages might need a full reload if they have complex dynamic content 
    // but for now, DOM replacement is cleaner
}

window.toggleLangDropdown = function() {
    const drop = document.getElementById('langDropdown');
    if (drop) drop.classList.toggle('active');
}

document.addEventListener('DOMContentLoaded', () => {
    const savedLang = localStorage.getItem('billme_lang') || 'en';
    applyLanguage(savedLang);
    
    // Dynamic year for footer
    const yearEl = document.getElementById('footerYear');
    if (yearEl) yearEl.innerText = new Date().getFullYear();
    document.querySelectorAll('.dynamic-year').forEach(el => {
      el.innerText = new Date().getFullYear();
    });

    // Handle clicks outside lang dropdown
    window.onclick = function (event) {
        if (!event.target.closest('.language-dropdown-container')) {
            const drop = document.getElementById('langDropdown');
            if (drop) drop.classList.remove('active');
        }
    }
});

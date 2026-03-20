/**
 * auth.js - BillMe Authentication & Session Persistence
 * Surgical fix for stability and zero 401 errors.
 */
(function() {
    const API_BASE_URL = window.location.origin.includes('localhost') || window.location.origin.includes('127.0.0.1') 
        ? 'http://localhost:8080' 
        : window.location.origin;

    const Auth = {
        user: null,

        login(token, refreshToken, role, userId) {
            localStorage.setItem('billme_token', token);
            localStorage.setItem('billme_refresh', refreshToken || '');
            localStorage.setItem('billme_role', (role || '').toLowerCase());
            localStorage.setItem('billme_user_id', userId || '');
        },

        logout() {
            localStorage.removeItem('billme_token');
            localStorage.removeItem('billme_refresh');
            localStorage.removeItem('billme_role');
            localStorage.removeItem('billme_user_id');
            this.user = null;
            
            if (!window.location.pathname.includes('login.html')) {
                window.location.href = '/frontend/Face_Payment-Shravani_UI/src/login.html';
            }
        },

        getToken() {
            const token = localStorage.getItem('billme_token');
            if (!token || token === 'undefined' || token === 'null' || token.trim() === '') {
                return null;
            }
            return token;
        },

        isAuthenticated() {
            return !!this.getToken();
        },

        // Get current user details from backend
        async fetchCurrentUser() {
            const token = this.getToken();
            if (!token) return null;

            try {
                const user = await window.API.auth.getMe();
                this.user = user;
                return user;
            } catch (error) {
                console.warn('Auth check skipped (not logged in or network error)');
                return null;
            }
        },

        updateUI(imageUrl) {
            const url = imageUrl || (this.user ? this.user.profileImageUrl : null);
            if (!url) return;

            // Update all elements with class 'user-avatar' or similar
            document.querySelectorAll('.topbar-avatar, .sidebar-profile__avatar, .profile-preview-img, #topbarAvatar, #side-avatar, #prof-avatar').forEach(el => {
                if (el.tagName === 'IMG') {
                    el.src = url;
                    el.onerror = () => {
                        el.style.display = 'none'; // Fallback logic could be complex, for now hide broken images
                    };
                } else {
                    el.innerHTML = `<img src="${url}" style="width:100%; height:100%; border-radius:50%; object-fit:cover;" onerror="this.parentElement.innerHTML='<i class=\'fas fa-user\'></i>'">`;
                    el.style.background = 'none';
                }
            });
        },

        bindProfileUpload() {
            const fileInput = document.getElementById('p-photo-input');
            const uploadBtn = document.getElementById('uploadPhotoBtn');
            const previewContainer = document.getElementById('profile-preview-container');

            if (!fileInput || !uploadBtn) return;

            fileInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file) {
                    // Preview
                    const reader = new FileReader();
                    reader.onload = (event) => {
                        if (previewContainer) {
                            previewContainer.innerHTML = `<img src="${event.target.result}" style="width:100%; height:100%; border-radius:50%; object-fit:cover;">`;
                            previewContainer.style.background = 'none';
                        }
                    };
                    reader.readAsDataURL(file);
                    uploadBtn.disabled = false;
                }
            });

            uploadBtn.addEventListener('click', async () => {
                const file = fileInput.files[0];
                if (!file) return;

                const formData = new FormData();
                formData.append('file', file);

                uploadBtn.disabled = true;
                const originalHtml = uploadBtn.innerHTML;
                uploadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Uploading...';

                try {
                    const response = await window.API.user.uploadProfilePhoto(formData);
                    if (response && response.imageUrl) {
                        if (this.user) this.user.profileImageUrl = response.imageUrl;
                        this.updateUI(response.imageUrl);
                        if (window.showToast) window.showToast('Profile photo updated!', 'success');
                        else alert('Profile photo updated!');
                    }
                } catch (error) {
                    console.error('Upload failed:', error);
                    if (window.showToast) window.showToast('Upload failed: ' + (error.message || 'Unknown error'), 'error');
                    else alert('Upload failed');
                } finally {
                    uploadBtn.disabled = false;
                    uploadBtn.innerHTML = originalHtml;
                    fileInput.value = '';
                }
            });
        },

        async initAuth() {
            const path = window.location.pathname;
            const isLoginPage = path.includes('login.html') || path.endsWith('/') || path.endsWith('index.html');
            const isRegisterPage = path.includes('register.html');
            const isDashboard = path.includes('/dashboard/');
            const token = this.getToken();

            // 1. If no token, allow public pages, redirect dashboards to login
            if (!token) {
                if (isDashboard) {
                    window.location.href = '/frontend/Face_Payment-Shravani_UI/src/login.html';
                }
                return;
            }

            // 2. Token exists - Validate via backend
            const user = await this.fetchCurrentUser();
            if (!user) {
                // If 401, API.js already handled redirect. 
                // We just return to stop execution.
                return;
            }

            // 3. Sync role and redirect if necessary
            const role = user.role.toLowerCase();
            localStorage.setItem('billme_role', role);

            const dashboards = {
                admin: '/frontend/Face_Payment-Shravani_UI/dashboard/Admin.html',
                merchant: '/frontend/Face_Payment-Shravani_UI/dashboard/merchant.html',
                customer: '/frontend/Face_Payment-Shravani_UI/dashboard/customer.html'
            };

            const target = dashboards[role];
            if (!target) return;

            const currentFile = path.split('/').pop().toLowerCase();
            const targetFile = target.split('/').pop().toLowerCase();

            // Handle landing/auth page -> dashboard redirect
            if (isLoginPage || isRegisterPage) {
                window.location.href = target;
                return;
            }

            // Handle wrong dashboard -> correct dashboard redirect
            if (isDashboard && currentFile !== targetFile) {
                window.location.href = target;
            }

            this.updateUI(user.profileImageUrl);
            this.bindProfileUpload();
        }
    };

    window.Auth = Auth;

    // Run on load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => Auth.initAuth());
    } else {
        Auth.initAuth();
    }
})();

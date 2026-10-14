// Products are loaded only from Spring Boot.
let products = [];
let productsFromApi = false;

function normalizeProduct(product) {
    return {
        ...product,
        key: String(product.key ?? product.id),
        id: product.id ?? product.key,
        img: imgFix(product.img),
        gallery: (product.gallery || []).map(imgFix)
    };
}

// Make image path absolute so it resolves from server root regardless of page location
// e.g. "Assets/Products/x.jpg" → "/Assets/Products/x.jpg"
function imgFix(p) {
    if (!p) return '';
    p = String(p).replace(/^\.\.\//, '');
    if (p.startsWith('http')) return p;
    if (!p.startsWith('/') && !p.startsWith('http')) p = '/' + p;
    return p.split('/').map(encodeURIComponent).join('/');
}

function getProductContent(page) {
    if (Array.isArray(page)) return page;
    if (page && Array.isArray(page.content)) return page.content;
    return [];
}

/**
 * Load products from Spring Boot API.
 * Maps ProductDto → the shape used by renderProducts():
 *   { key, id, name, category, img, gallery, price, rating, reviews, inStock, description }
 * Clears the catalog if Spring Boot is unreachable.
 */
async function loadProductsFromApi() {
    try {
        const content = await window.tapApi.products.listAll(100, 'newest');
        products = [];
        if (content.length > 0) {
            productsFromApi = true;
            products = content.map(mapApiProduct);
            // products loaded from API
        }
    } catch (e) {
        console.warn('Spring Boot is unavailable:', e.message);
        products = [];
    }
    await loadCustomerState();
    renderProducts();
    renderCart();
    renderSavedAddresses();
}

async function loadCustomerState() {
    if (!window.tapAuth?.isLoggedIn() || !window.tapApi) return;
    try {
        const summary = await window.tapApi.cart.get();
        Object.keys(cart).forEach(key => delete cart[key]);
        (summary.items || []).forEach(item => {
            cart[String(item.productId)] = {
                key: String(item.productId),
                id: item.productId,
                name: item.productName,
                img: imgFix(item.productImageUrl),
                category: item.categoryName || '',
                price: Number(item.unitPrice),
                qty: item.quantity,
                inStock: item.inStock
            };
        });
    } catch (e) {
        console.warn('Cart could not be loaded:', e.message);
    }
    try {
        const items = await window.tapApi.wishlist.list();
        wishlist.splice(0, wishlist.length, ...items.map(mapApiProduct));
    } catch (e) {
        console.warn('Wishlist could not be loaded:', e.message);
    }
}

window.addEventListener('tap:authenticated', async () => {
    await loadCustomerState();
    renderCart();
    renderProducts();
});


const hero = document.querySelector('.hero');
const heroHeading = document.querySelector('.hero-text h1');
const heroCopy = document.querySelector('.hero-text p');
const heroTrack = document.querySelector('.hero-carousel-track');
const heroDots = document.querySelector('.hero-dots');

if (heroHeading && heroCopy) {
    heroHeading.textContent = 'Welcome to 9tails';
    heroCopy.textContent = 'Discover the latest trends in fashion and accessories. Shop now and elevate your style!';
}

if (heroTrack && heroDots) {
    const slides = Array.from(heroTrack.children);
    let currentIndex = 0;
    let autoPlay;

    function renderDots() {
        heroDots.innerHTML = slides.map((_, index) => `
            <button class="hero-dot ${index === currentIndex ? 'active' : ''}" type="button" aria-label="Go to slide ${index + 1}"></button>
        `).join('');
        heroDots.querySelectorAll('.hero-dot').forEach((dot, index) => {
            dot.addEventListener('click', () => { currentIndex = index; updateCarousel(); });
        });
    }

    function updateCarousel() {
        heroTrack.style.transform = `translateX(-${currentIndex * 100}%)`;
        renderDots();
    }

    function startAutoPlay() {
        clearInterval(autoPlay);
        autoPlay = setInterval(() => {
            currentIndex = (currentIndex + 1) % slides.length;
            updateCarousel();
        }, 4000);
    }
    heroTrack.addEventListener('mouseleave', startAutoPlay);
    renderDots();
    updateCarousel();
    startAutoPlay();
}

const categoryButtons = document.querySelectorAll('.tab-button');
const productsGrid = document.getElementById('productsGrid');
const filterInputs = document.querySelectorAll('.filters-panel input');
const sortSelect = document.querySelector('.sort-select');
const cartToggle = document.querySelector('.cart-toggle');
const cartCountEl = document.querySelector('.cart-count');
const cartDrawer = document.getElementById('cartDrawer');
const closeCart = document.getElementById('closeCart');
const cartItemsEl = document.querySelector('.cart-items');
const cartTotalEl = document.getElementById('cartTotal');
const continueBtn = document.getElementById('continueBtn');
const addressPanel = document.getElementById('addressPanel');
const pageOverlay = document.getElementById('pageOverlay');
const addressForm = document.getElementById('addressForm');
const savedAddressesEl = document.getElementById('savedAddresses');
const saveAddressToggle = document.getElementById('saveAddressToggle');

let currentCategory = 'All';
let filters = { price: 'all', inStock: false, fastDelivery: false };
let sortBy = 'popular';
const cart = {};
let editedAddressIndex = null;
const savedAddresses = [];
let wishlist = [];

async function toggleWishlist(product, button) {
    if (!product) return null;
    const existingIndex = wishlist.findIndex(item => item.key === product.key);
    if (!window.tapAuth?.isLoggedIn()) {
        window.tapAuth?.requestLogin('Log in to save products to your wishlist.');
        return null;
    }
    if (!window.tapApi || !product.id) {
        window.tapAuth?.notify('error', 'Wishlist is unavailable. Refresh the page and try again.');
        return null;
    }
    if (button?.disabled) return null;
    if (button) button.disabled = true;
    try {
        const result = await window.tapApi.wishlist.toggle(product.id);
        const isAdded = result?.action ? result.action === 'added' : existingIndex < 0;
        if (isAdded && existingIndex < 0) wishlist.push({ ...product });
        if (!isAdded) {
            const currentIndex = wishlist.findIndex(item => item.key === product.key);
            if (currentIndex >= 0) wishlist.splice(currentIndex, 1);
        }
        return isAdded;
    } catch (e) {
        console.warn('Wishlist update failed:', e.message);
        if (!window.tapAuth?.isLoggedIn()) {
            window.tapAuth?.requestLogin('Your session expired. Sign in again to update your wishlist.');
        } else {
            window.tapAuth?.notify('error', e.message || 'Could not update your wishlist.');
        }
        return null;
    } finally {
        if (button) button.disabled = false;
    }
}

// Renders a star rating row — full, half and empty stars
function renderStars(rating) {
    const full = Math.floor(rating);
    const half = rating % 1 >= 0.5 ? 1 : 0;
    const empty = 5 - full - half;
    return (
        '<span class="star-full" aria-hidden="true">★</span>'.repeat(full) +
        (half ? '<span class="star-half" aria-hidden="true">★</span>' : '') +
        '<span class="star-empty" aria-hidden="true">★</span>'.repeat(empty)
    );
}

function openProductDetail(productKey) {
    // If key is a numeric DB id, use ?id= param; otherwise use legacy ?key= param
    const isNumeric = /^\d+$/.test(String(productKey));
    if (isNumeric) {
        window.location.href = `ProductDetail.html?id=${encodeURIComponent(productKey)}`;
    } else {
        window.location.href = `ProductDetail.html?key=${encodeURIComponent(productKey)}`;
    }
}

function getFilteredProducts() {
    return products
        .filter(product => {
            const categoryMatch = currentCategory === 'All' ||
                String(product.category || '').trim().toLowerCase() === currentCategory.trim().toLowerCase();
            const stockMatch = !filters.inStock || product.inStock;
            const deliveryMatch = !filters.fastDelivery || product.fastDelivery;
            let priceMatch = true;
            if (filters.price === 'under500') priceMatch = product.price < 500;
            if (filters.price === '500to1000') priceMatch = product.price >= 500 && product.price <= 1000;
            if (filters.price === 'above1000') priceMatch = product.price > 1000;
            return categoryMatch && stockMatch && deliveryMatch && priceMatch;
        })
        .sort((a, b) => {
            if (sortBy === 'low') return a.price - b.price;
            if (sortBy === 'high') return b.price - a.price;
            return a.name.localeCompare(b.name);
        });
}

function renderProducts() {
    const filtered = getFilteredProducts();
    if (!filtered.length) {
        productsGrid.innerHTML = '<p class="no-products">No products match your filters.</p>';
        return;
    }
    productsGrid.innerHTML = filtered.map(product => {
        const productKey = product.key;
        const rating = product.rating || 4.0;
        const reviews = product.reviews || 0;
        const fallbackImage = (product.gallery || []).find(image => image && image !== product.img) || '';
        return `
            <article class="product-card" data-id="${productKey}">
            <div class="product-image-wrap product-image-clickable" data-id="${productKey}" role="button" tabindex="0" aria-label="View ${product.name} details">
                <img src="${product.img}" data-fallback="${fallbackImage}" alt="${product.name}" />
                <div class="product-overlay-label">View Details</div>
                <button class="wishlist-btn ${wishlist.some(item => item.key === product.key) ? 'active' : ''}" data-id="${productKey}" aria-label="${wishlist.some(item => item.key === product.key) ? 'Remove from wishlist' : 'Add to wishlist'}" aria-pressed="${wishlist.some(item => item.key === product.key)}" type="button">${wishlist.some(item => item.key === product.key) ? '♥' : '♡'}</button>
            </div>
            <div class="product-info">
                <div class="product-info-text" data-id="${productKey}" role="button" tabindex="0" aria-label="View ${product.name} details">
                    <p class="product-category">${product.category}</p>
                    <h3>${product.name}</h3>
                </div>
                <strong>₹${product.price.toLocaleString('en-IN')}</strong>
            </div>
            <div class="product-rating" aria-label="Rating: ${rating} out of 5, ${reviews} reviews">
                <span class="stars">${renderStars(rating)}</span>
                <span class="rating-score">${rating}</span>
                <span class="rating-reviews">(${reviews})</span>
            </div>
            <div class="product-meta">
                <span class="product-stock ${product.inStock ? 'in-stock' : 'out-stock'}">${product.inStock ? 'In stock' : 'Out of stock'}</span>
                <button class="add-cart-btn" data-id="${productKey}" type="button" ${!product.inStock ? 'disabled' : ''}>Add to cart</button>
            </div>
        </article>
        `;
    }).join('');

    productsGrid.querySelectorAll('img[data-fallback]').forEach(image => {
        image.addEventListener('error', () => {
            const fallback = image.dataset.fallback;
            if (fallback && image.src !== new URL(fallback, window.location.href).href) {
                image.src = fallback;
            } else {
                image.removeAttribute('src');
                image.classList.add('image-unavailable');
            }
        }, { once: true });
    });

    // Add-to-cart buttons
    productsGrid.querySelectorAll('.add-cart-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            e.stopPropagation();
            addToCart(button.dataset.id, button);
        });
    });

    // Wishlist buttons
    productsGrid.querySelectorAll('.wishlist-btn').forEach(button => {
        button.addEventListener('click', async (e) => {
            e.stopPropagation();
            const product = products.find(item => item.key === button.dataset.id);
            const isAdded = await toggleWishlist(product, button);
            if (!product || isAdded === null) return;
            button.classList.toggle('active', isAdded);
            button.setAttribute('aria-pressed', String(isAdded));
            button.setAttribute('aria-label', isAdded ? 'Remove from wishlist' : 'Add to wishlist');
            button.textContent = isAdded ? '♥' : '♡';
            if (isAdded) {
                button.classList.remove('is-just-added');
                void button.offsetWidth;
                button.classList.add('is-just-added');
            }
        });
    });

    // Click on image or title → product detail
    productsGrid.querySelectorAll('.product-image-clickable').forEach(el => {
        el.addEventListener('click', () => openProductDetail(el.dataset.id));
        el.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openProductDetail(el.dataset.id); }
        });
    });
    productsGrid.querySelectorAll('.product-info-text').forEach(el => {
        el.addEventListener('click', () => openProductDetail(el.dataset.id));
        el.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openProductDetail(el.dataset.id); }
        });
    });
}

function mapApiProduct(p) {
    const gallery = Array.isArray(p.galleryImages)
        ? p.galleryImages.filter(Boolean).map(imgFix)
        : String(p.galleryImages || '').split(',').filter(Boolean).map(imgFix);
    return {
        key:          String(p.id),
        id:           p.id,
        name:         p.name,
        description:  p.description || '',
        category:     p.categoryName,
        img:          imgFix(p.imageUrl) || gallery[0] || '',
        gallery,
        price:        Number(p.price),
        originalPrice: p.originalPrice ? Number(p.originalPrice) : null,
        rating:       p.rating || 4.0,
        reviews:      p.reviewCount || 0,
        inStock:      Boolean(p.inStock),
        fastDelivery: p.fastDelivery === undefined ? Boolean(p.inStock) : Boolean(p.fastDelivery),
        stock:        p.stock
    };
}

function animateAddToCartButton(button) {
    if (!button) return;
    const originalLabel = button.dataset.label || button.textContent.trim() || 'Add to cart';
    if (!button.dataset.label) { button.dataset.label = originalLabel; }
    button.classList.add('is-success');
    button.innerHTML = '<span class="btn-checkmark">✓</span> Added';
    clearTimeout(button._cartResetTimer);
    button._cartResetTimer = setTimeout(() => {
        button.classList.remove('is-success');
        button.innerHTML = button.dataset.label;
    }, 900);
}

function updateCartStorage() {
    return cart;
}

function renderCart() {
    const items = Object.values(cart);
    const cartItemsContainer = cartItemsEl;
    const emptyMessage = document.querySelector('.empty-cart-message');
    if (!items.length) {
        emptyMessage.style.display = 'block';
        cartItemsContainer.innerHTML = '';
        cartTotalEl.textContent = '₹0.00';
        cartCountEl.textContent = '0';
        return;
    }
    emptyMessage.style.display = 'none';
    cartItemsContainer.innerHTML = items.map(item => `
        <div class="cart-item">
            <img src="${item.img}" alt="${item.name}" />
            <div class="cart-item-details">
                <p>${item.name}</p>
                <span>₹${item.price.toLocaleString('en-IN')} × ${item.qty}</span>
            </div>
            <div class="cart-item-actions">
                <button class="qty-btn" data-action="decrease" data-id="${item.key}">-</button>
                <span>${item.qty}</span>
                <button class="qty-btn" data-action="increase" data-id="${item.key}">+</button>
                <button class="remove-item" data-id="${item.key}" aria-label="Remove item">✕</button>
            </div>
        </div>
    `).join('');
    cartItemsContainer.querySelectorAll('.qty-btn').forEach(button => {
        button.addEventListener('click', () => changeQuantity(button.dataset.id, button.dataset.action));
    });
    cartItemsContainer.querySelectorAll('.remove-item').forEach(button => {
        button.addEventListener('click', () => removeCartItem(button.dataset.id));
    });
    const total = items.reduce((sum, item) => sum + item.price * item.qty, 0);
    cartTotalEl.textContent = `₹${total.toFixed(2)}`;
    const count = items.reduce((sum, item) => sum + item.qty, 0);
    cartCountEl.textContent = count;
}

async function addToCart(id, button) {
    const product = products.find(item => item.key === id);
    if (!product || !product.inStock) return;
    if (!window.tapAuth?.isLoggedIn()) {
        window.tapAuth?.requestLogin('Log in to add products to your cart.');
        return;
    }
    if (!window.tapApi || !product.id) {
        window.tapAuth?.notify('error', 'Cart is unavailable. Refresh the page and try again.');
        return;
    }
    if (button?.disabled) return;
    if (button) button.disabled = true;

    try {
        const summary = await window.tapApi.cart.add(product.id, 1);
        Object.keys(cart).forEach(key => delete cart[key]);
        (summary?.items || []).forEach(item => {
            cart[String(item.productId)] = {
                key: String(item.productId),
                id: item.productId,
                name: item.productName,
                img: imgFix(item.productImageUrl),
                category: item.categoryName || '',
                price: Number(item.unitPrice),
                qty: item.quantity,
                inStock: item.inStock
            };
        });
        updateCartStorage();
        renderCart();
        animateAddToCartButton(button);
    } catch (e) {
        console.warn('Cart add failed:', e.message);
        if (!window.tapAuth?.isLoggedIn()) {
            window.tapAuth?.requestLogin('Your session expired. Sign in again to add this product.');
        } else {
            window.tapAuth?.notify('error', e.message || 'Could not add this product to your cart.');
        }
    } finally {
        if (button) button.disabled = !product.inStock;
    }
}

async function removeCartItem(id) {
    const isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();
    if (isLoggedIn && window.tapApi) {
        try { await window.tapApi.cart.remove(id); }
        catch (e) { console.warn('Cart removal failed:', e.message); return; }
    }
    delete cart[id];
    updateCartStorage();
    renderCart();
}

async function changeQuantity(id, action) {
    if (!cart[id]) return;
    const previousQty = cart[id].qty;
    if (action === 'increase') cart[id].qty += 1;
    if (action === 'decrease') {
        cart[id].qty -= 1;
        if (cart[id].qty <= 0) delete cart[id];
    }
    const isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();
    if (isLoggedIn && window.tapApi) {
        try {
            if (cart[id]) await window.tapApi.cart.update(id, cart[id].qty);
            else await window.tapApi.cart.remove(id);
        } catch (e) {
            if (cart[id]) cart[id].qty = previousQty;
            else cart[id] = { ...products.find(item => item.key === id), qty: previousQty };
            console.warn('Cart quantity update failed:', e.message);
            renderCart();
            return;
        }
    }
    updateCartStorage();
    renderCart();
}

function toggleCart(open) {
    cartDrawer.classList.toggle('hidden', !open);
    pageOverlay.classList.toggle('hidden', !open);
    cartDrawer.setAttribute('aria-hidden', String(!open));
}

function renderSavedAddresses() {
    if (!savedAddresses.length) {
        savedAddressesEl.innerHTML = '<p class="saved-empty">No saved address yet.</p>';
        return;
    }
    savedAddressesEl.innerHTML = savedAddresses.map((address, index) => `
        <div class="saved-address-card ${address.selected ? 'selected' : ''}">
            <div>
                <strong>${address.fullName}</strong>
                <p>${address.street}, ${address.city}, ${address.postalCode}, ${address.country}</p>
            </div>
            <div class="address-actions">
                <button class="edit-address" data-index="${index}">✎</button>
                <button class="delete-address" data-index="${index}">🗑</button>
            </div>
        </div>
    `).join('');
    savedAddressesEl.querySelectorAll('.edit-address').forEach(btn => {
        btn.addEventListener('click', () => editAddress(Number(btn.dataset.index)));
    });
    savedAddressesEl.querySelectorAll('.delete-address').forEach(btn => {
        btn.addEventListener('click', () => deleteAddress(Number(btn.dataset.index)));
    });
}

function editAddress(index) {
    const address = savedAddresses[index];
    if (!address) return;
    editedAddressIndex = index;
    document.getElementById('fullName').value = address.fullName;
    document.getElementById('street').value = address.street;
    document.getElementById('city').value = address.city;
    document.getElementById('postalCode').value = address.postalCode;
    document.getElementById('country').value = address.country;
    addressForm.scrollIntoView({ behavior: 'smooth' });
}

function deleteAddress(index) {
    savedAddresses.splice(index, 1);
    renderSavedAddresses();
}

function showAddressPanel() {
    addressPanel.classList.remove('hidden');
    renderSavedAddresses();
}

addressForm.addEventListener('submit', event => {
    event.preventDefault();
    const address = {
        fullName: document.getElementById('fullName').value,
        street: document.getElementById('street').value,
        city: document.getElementById('city').value,
        postalCode: document.getElementById('postalCode').value,
        country: document.getElementById('country').value,
        selected: true
    };
    if (editedAddressIndex !== null) {
        savedAddresses[editedAddressIndex] = address;
        editedAddressIndex = null;
    } else {
        if (saveAddressToggle.checked) { savedAddresses.push(address); }
    }
    renderSavedAddresses();
    addressForm.reset();
    // Address saved — no alert needed, the UI updates automatically
});

continueBtn.addEventListener('click', () => {
    if (!Object.keys(cart).length) {
        // Cart empty — do nothing, continueBtn is disabled
        return;
    }
    showAddressPanel();
});

cartToggle.addEventListener('click', () => { window.location.href = './Cart.html'; });
closeCart.addEventListener('click', () => toggleCart(false));
pageOverlay.addEventListener('click', () => toggleCart(false));

categoryButtons.forEach(button => {
    button.addEventListener('click', async () => {
        categoryButtons.forEach(btn => btn.classList.remove('active'));
        button.classList.add('active');
        currentCategory = (button.dataset.category || 'All').trim();

        // If products came from API, filter server-side for accuracy
        renderProducts();

        if (productsFromApi && products.length > 0 && products[0].id) {
            try {
                if (currentCategory === 'All') {
                    const content = await window.tapApi.products.listAll(100, sortBy === 'low' ? 'price-low' : sortBy === 'high' ? 'price-high' : 'newest');
                    products = content.map(mapApiProduct);
                } else {
                    const content = await window.tapApi.products.byCategoryAll(currentCategory, 100, 'newest');
                    products = content.map(mapApiProduct);
                }
                renderProducts();
            } catch(e) { /* keep the immediately rendered catalog */ }
        }
    });
});

filterInputs.forEach(input => {
    input.addEventListener('change', () => {
        if (input.name === 'price') { filters.price = input.value; }
        if (input.type === 'checkbox') {
            if (input.value === 'inStock') filters.inStock = input.checked;
            if (input.value === 'fastDelivery') filters.fastDelivery = input.checked;
        }
        renderProducts();
    });
});

sortSelect.addEventListener('change', () => {
    sortBy = sortSelect.value;
    renderProducts();
});

// ── Init: load products from API, then render everything ──────────────────
loadProductsFromApi();

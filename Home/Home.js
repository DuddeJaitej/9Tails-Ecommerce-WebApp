// products array — loaded from API (with products-data.js as fallback)
let products = (typeof window._productsData !== 'undefined') ? window._productsData : [];

/**
 * Load products from Spring Boot API.
 * Maps ProductDto → the shape used by renderProducts():
 *   { key, id, name, category, img, gallery, price, rating, reviews, inStock, description }
 * Falls back to the global products array from products-data.js if API is unreachable.
 */
async function loadProductsFromApi() {
    try {
        const page = await window.tapApi.products.list(0, 200, 'newest');
        if (page && page.content && page.content.length > 0) {
            products = page.content.map(p => ({
                key:         String(p.id),          // numeric DB id as string key
                id:          p.id,                  // numeric — used for API cart calls
                name:        p.name,
                description: p.description || '',
                category:    p.categoryName,
                img:         p.imageUrl || '',
                gallery:     p.galleryImages || [],  // array from API
                price:       Number(p.price),
                originalPrice: p.originalPrice ? Number(p.originalPrice) : null,
                rating:      p.rating || 4.0,
                reviews:     p.reviewCount || 0,
                inStock:     p.inStock,
                stock:       p.stock
            }));
            // products loaded from API
        }
    } catch (e) {
        console.warn('API unavailable — using local products-data.js:', e.message);
        // products-data.js already set the global `products` array — nothing to do
    }
    renderProducts();
    renderCart();
    renderSavedAddresses();
}


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

    heroTrack.addEventListener('mouseenter', () => clearInterval(autoPlay));
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
const savedAddressesEl = document.getElementById('savedAddresses');
const addressForm = document.getElementById('addressForm');
const saveAddressToggle = document.getElementById('saveAddressToggle');

let currentCategory = 'All';
let filters = { price: 'all', inStock: false, fastDelivery: false };
let sortBy = 'popular';
const cart = JSON.parse(localStorage.getItem('tapCart') || '{}');
let editedAddressIndex = null;
const savedAddresses = JSON.parse(localStorage.getItem('tapAddresses') || '[]');
const wishlistKey = 'tapWishlist';
let wishlist = JSON.parse(localStorage.getItem(wishlistKey) || '[]');

function updateWishlistStorage() {
    localStorage.setItem(wishlistKey, JSON.stringify(wishlist));
}

function toggleWishlist(product) {
    if (!product) return;
    const existingIndex = wishlist.findIndex(item => item.key === product.key);
    if (existingIndex >= 0) {
        wishlist.splice(existingIndex, 1);
    } else {
        wishlist.push({ ...product });
    }
    updateWishlistStorage();
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
            const categoryMatch = currentCategory === 'All' || product.category === currentCategory;
            const stockMatch = !filters.inStock || product.inStock;
            const deliveryMatch = !filters.fastDelivery || product.fastDelivery;
            let priceMatch = true;
            if (filters.price === 'under50') priceMatch = product.price < 50;
            if (filters.price === '50to150') priceMatch = product.price >= 50 && product.price <= 150;
            if (filters.price === 'above150') priceMatch = product.price > 150;
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
        return `
        <article class="product-card" data-id="${productKey}">
            <div class="product-image-wrap product-image-clickable" data-id="${productKey}" role="button" tabindex="0" aria-label="View ${product.name} details">
                <img src="${product.img}" alt="${product.name}" />
                <div class="product-overlay-label">View Details</div>
                <button class="wishlist-btn ${wishlist.some(item => item.key === product.key) ? 'active' : ''}" data-id="${productKey}" aria-label="Add to wishlist" type="button">♥</button>
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

    // Add-to-cart buttons
    productsGrid.querySelectorAll('.add-cart-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            e.stopPropagation();
            addToCart(button.dataset.id, button);
        });
    });

    // Wishlist buttons
    productsGrid.querySelectorAll('.wishlist-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            e.stopPropagation();
            const product = products.find(item => item.key === button.dataset.id);
            toggleWishlist(product);
            renderProducts();
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
    return {
        key:          String(p.id),
        id:           p.id,
        name:         p.name,
        description:  p.description || '',
        category:     p.categoryName,
        img:          p.imageUrl || '',
        gallery:      p.galleryImages || [],
        price:        Number(p.price),
        originalPrice: p.originalPrice ? Number(p.originalPrice) : null,
        rating:       p.rating || 4.0,
        reviews:      p.reviewCount || 0,
        inStock:      p.inStock,
        stock:        p.stock
    };
}


    localStorage.setItem('tapCart', JSON.stringify(cart));
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

function addToCart(id, button) {
    const product = products.find(item => item.key === id);
    if (!product || !product.inStock) return;

    // Optimistic local update
    if (cart[id]) { cart[id].qty += 1; }
    else { cart[id] = { ...product, qty: 1 }; }
    updateCartStorage();
    renderCart();
    animateAddToCartButton(button);

    // API call (non-blocking) — product.id is the numeric DB id
    const numericId = product.id || product.key;
    if (window.tapAuth && window.tapAuth.isLoggedIn() && window.tapApi) {
        window.tapApi.cart.add(numericId, 1).catch(() => {/* ignore, local state already updated */});
    }
}

function removeCartItem(id) {
    delete cart[id];
    updateCartStorage();
    renderCart();
}

function changeQuantity(id, action) {
    if (!cart[id]) return;
    if (action === 'increase') cart[id].qty += 1;
    if (action === 'decrease') {
        cart[id].qty -= 1;
        if (cart[id].qty <= 0) delete cart[id];
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
    localStorage.setItem('tapAddresses', JSON.stringify(savedAddresses));
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
    localStorage.setItem('tapAddresses', JSON.stringify(savedAddresses));
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
        currentCategory = button.dataset.category;

        // If products came from API, filter server-side for accuracy
        if (products.length > 0 && products[0].id) {
            try {
                if (currentCategory === 'All') {
                    const page = await window.tapApi.products.list(0, 200, sortBy === 'low' ? 'price-low' : sortBy === 'high' ? 'price-high' : 'newest');
                    products = page.content.map(mapApiProduct);
                } else {
                    const page = await window.tapApi.products.byCategory(currentCategory, 0, 200, 'newest');
                    products = page.content.map(mapApiProduct);
                }
            } catch(e) { /* keep current products array */ }
        }
        renderProducts();
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

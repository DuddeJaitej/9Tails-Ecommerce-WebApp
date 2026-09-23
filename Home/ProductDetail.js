// ProductDetail.js — handles image gallery, add-to-cart, wishlist, related products

(function () {
    const qs = selector => document.querySelector(selector);

    // Normalize image path — make absolute so it always resolves from server root
    // API returns "Assets/Products/..." → we need "/Assets/Products/..."
    function imgFix(p) {
        if (!p) return '';
        p = p.replace(/^\.\.\//, '');          // strip ../
        if (!p.startsWith('/')) p = '/' + p;  // ensure leading /
        return p;
    }

    function formatPrice(n) {
        return '₹' + Number(n).toLocaleString('en-IN');
    }


    function renderStars(rating) {
        const full  = Math.floor(rating);
        const half  = (rating % 1) >= 0.5 ? 1 : 0;
        const empty = 5 - full - half;
        const s = (cls, ch) => `<span class="${cls}" aria-hidden="true">${ch}</span>`;
        return s('star-full','★').repeat(full) +
               (half ? s('star-half','★') : '') +
               s('star-empty','★').repeat(empty);
    }

    // ── Read product — API first (by ?id=), fallback to ?key= from products-data.js ──
    const params     = new URLSearchParams(window.location.search);
    const productId  = params.get('id');   // numeric DB id (from API-loaded pages)
    const productKey = params.get('key');  // legacy string key (from products-data.js)

    // Show loading state
    const titleEl = document.getElementById('pdTitle');
    if (titleEl) titleEl.textContent = 'Loading…';

    async function loadAndRender() {
        let product = null;

        // ── Try API by numeric id ──────────────────────────────────────────
        if (productId && window.tapApi) {
            try {
                const dto = await window.tapApi.products.get(productId);
                // Map ProductDto → shape used below
                product = {
                    key:          String(dto.id),
                    id:           dto.id,
                    name:         dto.name,
                    description:  dto.description || '',
                    category:     dto.categoryName,
                    img:          imgFix(dto.imageUrl),
                    // gallery: API returns string array
                    gallery:      Array.isArray(dto.galleryImages)
                                    ? dto.galleryImages
                                    : (dto.galleryImages || '').split(',').filter(Boolean),
                    price:        Number(dto.price),
                    originalPrice: dto.originalPrice ? Number(dto.originalPrice) : null,
                    rating:       dto.rating || 4.0,
                    reviews:      dto.reviewCount || 0,
                    inStock:      dto.inStock,
                    stock:        dto.stock
                };

                // Load related products from API
                try {
                    const relDtos = await window.tapApi.products.related(productId);
                    window._relatedProducts = (relDtos || []).map(p => ({
                        key:      String(p.id), id: p.id, name: p.name,
                        category: p.categoryName, img: imgFix(p.imageUrl),
                        price:    Number(p.price), rating: p.rating || 4.0,
                        reviews:  p.reviewCount || 0, inStock: p.inStock
                    }));
                } catch(e) { window._relatedProducts = []; }

            } catch(e) {
                console.warn('API product fetch failed, trying local data:', e.message);
            }
        }

        // ── Fallback: products-data.js by key ──────────────────────────────
        const localProducts = (window._productsData || []).map(p => ({
            ...p,
            key: String(p.key ?? p.id),
            id: p.id ?? p.key,
            img: imgFix(p.img),
            gallery: (p.gallery || []).map(imgFix)
        }));

        if (!product && productKey) {
            const local = localProducts.find(p => p.key === productKey);
            if (local) product = local;
        }

        // ── Also try matching by id in local products array ────────────────
        if (!product && productId) {
            const local = localProducts.find(p => String(p.id) === String(productId) || p.key === String(productId));
            if (local) product = local;
        }

        if (!product) {
            if (titleEl) titleEl.textContent = 'Product not found.';
            const descEl = document.getElementById('pdDescription');
            if (descEl) descEl.textContent = 'Sorry, we could not find this product. Go back to Home.';
            return;
        }

        renderProduct(product);
    }

    // kick off
    loadAndRender();
    return; // rest of IIFE runs inside renderProduct()

    function renderProduct(product) {
    document.title = `${product.name} — 9Tails`;
    qs('#breadcrumbCategory').textContent = product.category;
    qs('#breadcrumbName').textContent     = product.name;

    // ── Populate static info fields ───────────────────────────────────────────
    qs('#pdCategory').textContent    = product.category;
    qs('#pdTitle').textContent       = product.name;
    qs('#pdPrice').textContent       = formatPrice(product.price);
    qs('#pdDescription').textContent = product.description || 'No description available.';

    const ratingVal  = product.rating  || 4.0;
    const reviewsVal = product.reviews || 0;
    qs('#pdStars').innerHTML        = renderStars(ratingVal);
    qs('#pdRatingScore').textContent = ratingVal.toFixed(1);
    qs('#pdReviews').textContent     = `(${reviewsVal.toLocaleString()} reviews)`;

    const stockEl = qs('#pdStock');
    if (product.inStock) {
        stockEl.textContent  = 'In Stock';
        stockEl.className    = 'pd-stock-badge in-stock';
    } else {
        stockEl.textContent  = 'Out of Stock';
        stockEl.className    = 'pd-stock-badge out-stock';
    }

    const deliveryEl = qs('#pdDelivery');
    if (product.fastDelivery) {
        deliveryEl.textContent = '⚡ Fast Delivery';
        deliveryEl.style.display = 'inline-flex';
    } else {
        deliveryEl.style.display = 'none';
    }

    // ── Single image — no gallery strip ─────────────────────────────────────
    const mainImageEl  = qs('#pdMainImage');
    const thumbnailsEl = qs('#pdThumbnails');

    // Show only the one main product image
    const singleImg = imgFix(product.img || '');
    const gallery = [singleImg].filter(Boolean);
    if (mainImageEl) {
        mainImageEl.src = singleImg;
        mainImageEl.alt = product.name;
    }

    // Hide the thumbnail strip entirely — single image per product
    if (thumbnailsEl) thumbnailsEl.style.display = 'none';

    // Also hide the gallery wrapper/sidebar if it exists
    const galleryWrapper = qs('.pd-gallery-side, .pd-thumbnails-wrap, #pdThumbnails');
    if (galleryWrapper) galleryWrapper.style.display = 'none';

    // ── Lightbox (zoom on click) ───────────────────────────────────────────────
    const lightbox       = qs('#pdLightbox');
    const lightboxImg    = qs('#pdLightboxImg');
    const lightboxClose  = qs('#pdLightboxClose');

    if (mainImageEl && lightbox && lightboxImg && lightboxClose && gallery.length) {
        mainImageEl.addEventListener('click', () => {
            lightboxImg.src = gallery[0];
            lightbox.classList.remove('hidden');
            document.body.style.overflow = 'hidden';
            lightboxClose.focus();
        });
    }

    function closeLightbox() {
        lightbox.classList.add('hidden');
        document.body.style.overflow = '';
        mainImageEl.focus();
    }

    if (lightboxClose) lightboxClose.addEventListener('click', closeLightbox);
    if (lightbox) lightbox.addEventListener('click', e => { if (e.target === lightbox) closeLightbox(); });
    document.addEventListener('keydown', e => {
        if (!lightbox.classList.contains('hidden')) {
            if (e.key === 'Escape') closeLightbox();
        }
    });

    // ── Quantity control ──────────────────────────────────────────────────────
    let qty = 1;
    const qtyValEl = qs('#pdQtyVal');

    qs('#pdQtyInc').addEventListener('click', () => {
        qty++;
        qtyValEl.textContent = qty;
    });

    qs('#pdQtyDec').addEventListener('click', () => {
        if (qty > 1) { qty--; qtyValEl.textContent = qty; }
    });

    // ── Cart ──────────────────────────────────────────────────────────────────
    const cart = JSON.parse(localStorage.getItem('tapCart') || '{}');
    const cartCountEl = qs('.cart-count');

    function syncCartCount() {
        const count = Object.values(cart).reduce((s, i) => s + (i.qty || 0), 0);
        if (cartCountEl) cartCountEl.textContent = count;
    }
    syncCartCount();

    function saveCart() {
        localStorage.setItem('tapCart', JSON.stringify(cart));
        syncCartCount();
    }

    const addCartBtn = qs('#pdAddCart');

    if (!product.inStock) {
        addCartBtn.disabled = true;
        addCartBtn.textContent = 'Out of Stock';
    }

    addCartBtn.addEventListener('click', () => {
        if (!product.inStock) return;
        const key = product.key;
        if (cart[key]) {
            cart[key].qty += qty;
        } else {
            cart[key] = { ...product, qty };
        }
        saveCart();

        // Button feedback
        const orig = addCartBtn.textContent;
        addCartBtn.classList.add('is-success');
        addCartBtn.innerHTML = '<span class="btn-checkmark">✓</span> Added to Cart';
        clearTimeout(addCartBtn._timer);
        addCartBtn._timer = setTimeout(() => {
            addCartBtn.classList.remove('is-success');
            addCartBtn.textContent = orig;
        }, 1200);
    });

    // Cart icon → cart page
    const cartToggle = qs('.cart-toggle');
    if (cartToggle) {
        cartToggle.addEventListener('click', () => { window.location.href = './Cart.html'; });
    }

    // ── Wishlist ──────────────────────────────────────────────────────────────
    const wishlist     = JSON.parse(localStorage.getItem('tapWishlist') || '[]');
    const wishlistBtn  = qs('#pdWishlist');
    let isWishlisted   = wishlist.some(i => i.key === product.key);

    function updateWishlistBtn() {
        wishlistBtn.classList.toggle('active', isWishlisted);
        wishlistBtn.textContent = isWishlisted ? '♥ Wishlisted' : '♥ Wishlist';
        wishlistBtn.setAttribute('aria-pressed', String(isWishlisted));
    }
    updateWishlistBtn();

    wishlistBtn.addEventListener('click', () => {
        if (isWishlisted) {
            const idx = wishlist.findIndex(i => i.key === product.key);
            if (idx >= 0) wishlist.splice(idx, 1);
        } else {
            wishlist.push({ ...product });
        }
        isWishlisted = !isWishlisted;
        localStorage.setItem('tapWishlist', JSON.stringify(wishlist));
        updateWishlistBtn();
    });

    // ── Related products — use API data if available, else local filter ──────
    const relatedGrid = qs('#pdRelatedGrid');
    // API stores related in window._relatedProducts; fallback to local filter
    const localProducts = (window._productsData || []).map(p => ({
        ...p,
        key: String(p.key ?? p.id),
        id: p.id ?? p.key,
        img: imgFix(p.img)
    }));
    const related = (window._relatedProducts && window._relatedProducts.length > 0)
        ? window._relatedProducts.slice(0, 4)
        : localProducts.filter(p => p.category === product.category && p.key !== product.key).slice(0, 4);

    if (related.length) {
        relatedGrid.innerHTML = related.map(p => `
            <article class="product-card related-product-card">
                <div class="product-image-wrap product-image-clickable"
                     data-key="${p.key}"
                     role="button" tabindex="0"
                     aria-label="View ${p.name} details">
                    <img src="${imgFix(p.img)}" alt="${p.name}" />
                    <div class="product-overlay-label">View Details</div>
                </div>
                <div class="product-info">
                    <div class="product-info-text" data-key="${p.key}" role="button" tabindex="0">
                        <p class="product-category">${p.category}</p>
                        <h3>${p.name}</h3>
                    </div>
                    <strong>${formatPrice(p.price)}</strong>
                </div>
                <div class="product-rating" aria-label="Rating: ${p.rating} out of 5">
                    <span class="stars">${renderStars(p.rating || 4)}</span>
                    <span class="rating-score">${(p.rating || 4).toFixed(1)}</span>
                    <span class="rating-reviews">(${(p.reviews || 0).toLocaleString()})</span>
                </div>
                <div class="product-meta">
                    <span class="product-stock ${p.inStock ? 'in-stock' : 'out-stock'}">${p.inStock ? 'In stock' : 'Out of stock'}</span>
                    <button class="add-cart-btn related-cart-btn" data-key="${p.key}" type="button" ${!p.inStock ? 'disabled' : ''}>Add to cart</button>
                </div>
            </article>
        `).join('');

        // Navigate to product detail — use numeric id if available
        relatedGrid.querySelectorAll('.product-image-clickable, .product-info-text').forEach(el => {
            const handler = () => {
                const k = el.dataset.key;
                const isNumeric = /^\d+$/.test(String(k));
                window.location.href = isNumeric
                    ? `ProductDetail.html?id=${encodeURIComponent(k)}`
                    : `ProductDetail.html?key=${encodeURIComponent(k)}`;
            };
            el.addEventListener('click', handler);
            el.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handler(); } });
        });

        // Related add-to-cart
        relatedGrid.querySelectorAll('.related-cart-btn').forEach(btn => {
            btn.addEventListener('click', e => {
                e.stopPropagation();
                const relProduct = related.find(p => p.key === btn.dataset.key);
                if (!relProduct || !relProduct.inStock) return;
                const k = relProduct.key;
                if (cart[k]) { cart[k].qty += 1; } else { cart[k] = { ...relProduct, qty: 1 }; }
                saveCart();
                // API sync
                if (window.tapAuth && window.tapAuth.isLoggedIn() && window.tapApi && relProduct.id) {
                    window.tapApi.cart.add(relProduct.id, 1).catch(() => {});
                }
                const orig = btn.textContent;
                btn.classList.add('is-success');
                btn.innerHTML = '<span class="btn-checkmark">✓</span> Added';
                clearTimeout(btn._timer);
                btn._timer = setTimeout(() => { btn.classList.remove('is-success'); btn.textContent = orig; }, 900);
            });
        });
    } else {
        const relSection = qs('#pdRelatedSection');
        if (relSection) relSection.style.display = 'none';
    }

    } // end renderProduct()

})();

// ProductDetail.js — handles image gallery, add-to-cart, wishlist, related products

(function () {
    const qs = selector => document.querySelector(selector);

    // Normalize image path — make absolute so it always resolves from server root
    // API returns "Assets/Products/..." → we need "/Assets/Products/..."
    function imgFix(p) {
        if (!p) return '';
        p = p.replace(/^\.\.\//, '');          // strip ../
        if (p.startsWith('http')) return p;
        if (!p.startsWith('/')) p = '/' + p;  // ensure leading /
        return p.split('/').map(segment => {
            try {
                return encodeURIComponent(decodeURIComponent(segment));
            } catch {
                return encodeURIComponent(segment);
            }
        }).join('/');
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

    // ── Read product only from Spring Boot ──
    const params     = new URLSearchParams(window.location.search);
    const productId  = params.get('id');   // numeric DB id (from API-loaded pages)

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
                                    ? dto.galleryImages.filter(Boolean).map(imgFix)
                                    : (dto.galleryImages || '').split(',').filter(Boolean).map(imgFix),
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
                } catch (e) {
                    window._relatedProducts = [];
                    console.warn('Related products could not be loaded:', e.message);
                }

            } catch(e) {
                console.warn('Spring Boot product fetch failed:', e.message);
            }
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
    qs('#pdQtyInc').disabled = !product.inStock || product.stock <= 1;
    qs('#pdQtyDec').disabled = true;

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
    const gallery = [...new Set([singleImg, ...(product.gallery || [])].filter(Boolean))];
    if (mainImageEl) {
        mainImageEl.src = singleImg;
        mainImageEl.alt = product.name;
        mainImageEl.addEventListener('error', () => {
            const fallback = gallery.find(src => src !== singleImg);
            if (fallback && mainImageEl.src !== new URL(fallback, window.location.href).href) {
                mainImageEl.src = fallback;
            }
        }, { once: true });
    }

    if (thumbnailsEl) {
        thumbnailsEl.innerHTML = gallery.map((src, index) => `
            <button type="button" class="pd-thumb ${index === 0 ? 'active' : ''}" data-src="${src}" aria-label="View image ${index + 1}">
                <img src="${src}" alt="${product.name} image ${index + 1}" />
            </button>
        `).join('');
        thumbnailsEl.style.display = gallery.length > 1 ? '' : 'none';
        thumbnailsEl.querySelectorAll('.pd-thumb').forEach(button => {
            button.addEventListener('click', () => {
                mainImageEl.src = button.dataset.src;
                thumbnailsEl.querySelectorAll('.pd-thumb').forEach(item => item.classList.remove('active'));
                button.classList.add('active');
            });
        });
    }

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
        if (product.stock && qty >= product.stock) return;
        qty++;
        qtyValEl.textContent = qty;
        qs('#pdQtyDec').disabled = qty <= 1;
        qs('#pdQtyInc').disabled = Boolean(product.stock && qty >= product.stock);
    });

    qs('#pdQtyDec').addEventListener('click', () => {
        if (qty > 1) {
            qty--;
            qtyValEl.textContent = qty;
            qs('#pdQtyDec').disabled = qty <= 1;
            qs('#pdQtyInc').disabled = Boolean(product.stock && qty >= product.stock);
        }
    });

    // ── Cart ──────────────────────────────────────────────────────────────────
    function syncCartCount() {
        if (window.tapAuth) return window.tapAuth.syncCartCountFromApi();
        return Promise.resolve();
    }
    syncCartCount();

    const addCartBtn = qs('#pdAddCart');

    if (!product.inStock) {
        addCartBtn.disabled = true;
        addCartBtn.textContent = 'Out of Stock';
    }

    addCartBtn.addEventListener('click', async () => {
        if (!product.inStock) return;
        const isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();
        if (!isLoggedIn) {
            window.tapAuth?.requestLogin('Log in to add this product to your cart.');
            return;
        }
        if (!window.tapApi || !product.id) {
            window.tapAuth?.notify('error', 'Cart is unavailable. Refresh the page and try again.');
            return;
        }
        if (addCartBtn.disabled) return;
        addCartBtn.disabled = true;
        try {
            await window.tapApi.cart.add(product.id, qty);
            await syncCartCount();
            const orig = addCartBtn.dataset.label || 'Add to Cart';
            addCartBtn.dataset.label = orig;
            addCartBtn.classList.add('is-success');
            addCartBtn.innerHTML = '<span class="btn-checkmark">✓</span> Added to Cart';
            clearTimeout(addCartBtn._timer);
            addCartBtn._timer = setTimeout(() => {
                addCartBtn.classList.remove('is-success');
                addCartBtn.textContent = orig;
            }, 1200);
        } catch (e) {
            console.error('Product could not be added to cart:', e.message);
            if (!window.tapAuth?.isLoggedIn()) {
                window.tapAuth?.requestLogin('Your session expired. Sign in again to add this product.');
            } else {
                window.tapAuth?.notify('error', e.message || 'Could not add this product to your cart.');
            }
        } finally {
            addCartBtn.disabled = !product.inStock;
        }
    });

    // Cart icon → cart page
    const cartToggle = qs('.cart-toggle');
    if (cartToggle) {
        cartToggle.addEventListener('click', () => { window.location.href = './Cart.html'; });
    }

    // ── Wishlist ──────────────────────────────────────────────────────────────
    const wishlistBtn  = qs('#pdWishlist');
    let isWishlisted   = false;
    let wishlistStatusRequest = 0;

    function updateWishlistBtn() {
        wishlistBtn.classList.toggle('active', isWishlisted);
        wishlistBtn.textContent = isWishlisted ? '♥ Wishlisted' : '♥ Wishlist';
        wishlistBtn.setAttribute('aria-label', isWishlisted ? 'Remove from wishlist' : 'Add to wishlist');
        wishlistBtn.setAttribute('aria-pressed', String(isWishlisted));
    }
    updateWishlistBtn();

    async function refreshWishlistStatus() {
        const requestId = ++wishlistStatusRequest;
        if (!window.tapAuth?.isLoggedIn() || !window.tapApi || !product.id) {
            isWishlisted = false;
            updateWishlistBtn();
            return;
        }
        try {
            const result = await window.tapApi.wishlist.status(product.id);
            if (requestId !== wishlistStatusRequest) return;
            isWishlisted = Boolean(result?.wishlisted);
            updateWishlistBtn();
        } catch (e) {
            if (requestId === wishlistStatusRequest) {
                console.warn('Wishlist status could not be loaded:', e.message);
            }
        }
    }
    refreshWishlistStatus();
    window.addEventListener('tap:authenticated', refreshWishlistStatus);

    wishlistBtn.addEventListener('click', async () => {
        const isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();
        if (!isLoggedIn) {
            window.tapAuth?.requestLogin('Log in to save this product to your wishlist.');
            return;
        }
        if (!window.tapApi || !product.id) {
            window.tapAuth?.notify('error', 'Wishlist is unavailable. Refresh the page and try again.');
            return;
        }
        if (wishlistBtn.disabled) return;
        wishlistBtn.disabled = true;
        wishlistStatusRequest += 1;
        try {
            const result = await window.tapApi.wishlist.toggle(product.id);
            isWishlisted = result?.action ? result.action === 'added' : !isWishlisted;
            updateWishlistBtn();
            if (isWishlisted) {
                wishlistBtn.classList.remove('is-just-added');
                void wishlistBtn.offsetWidth;
                wishlistBtn.classList.add('is-just-added');
                clearTimeout(wishlistBtn._timer);
                wishlistBtn._timer = setTimeout(() => wishlistBtn.classList.remove('is-just-added'), 700);
            }
        } catch (e) {
            console.error('Wishlist could not be updated:', e.message);
            if (!window.tapAuth?.isLoggedIn()) {
                window.tapAuth?.requestLogin('Your session expired. Sign in again to update your wishlist.');
            } else {
                window.tapAuth?.notify('error', e.message || 'Could not update your wishlist.');
            }
        } finally {
            wishlistBtn.disabled = false;
        }
    });

    // ── Related products loaded from the API ─────────────────────────────────
    const relatedGrid = qs('#pdRelatedGrid');
    const related = (window._relatedProducts || []).slice(0, 4);

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
            btn.addEventListener('click', async e => {
                e.stopPropagation();
                const relProduct = related.find(p => p.key === btn.dataset.key);
                if (!relProduct || !relProduct.inStock) return;
                if (!window.tapAuth?.isLoggedIn()) {
                    window.tapAuth?.requestLogin('Log in to add this product to your cart.');
                    return;
                }
                if (!window.tapApi) {
                    window.tapAuth?.notify('error', 'Cart is unavailable. Refresh the page and try again.');
                    return;
                }
                if (btn.disabled) return;
                btn.disabled = true;
                try {
                    await window.tapApi.cart.add(relProduct.id, 1);
                    await syncCartCount();
                    const orig = btn.dataset.label || 'Add to cart';
                    btn.dataset.label = orig;
                    btn.classList.add('is-success');
                    btn.innerHTML = '<span class="btn-checkmark">✓</span> Added';
                    clearTimeout(btn._timer);
                    btn._timer = setTimeout(() => { btn.classList.remove('is-success'); btn.textContent = orig; }, 900);
                } catch (e) {
                    console.error('Related product could not be added to cart:', e.message);
                    if (!window.tapAuth?.isLoggedIn()) {
                        window.tapAuth?.requestLogin('Your session expired. Sign in again to add this product.');
                    } else {
                        window.tapAuth?.notify('error', e.message || 'Could not add this product to your cart.');
                    }
                } finally {
                    btn.disabled = !relProduct.inStock;
                }
            });
        });
    } else {
        const relSection = qs('#pdRelatedSection');
        if (relSection) relSection.style.display = 'none';
    }

    } // end renderProduct()

})();

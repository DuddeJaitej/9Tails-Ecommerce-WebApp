// Enhanced product catalog with ratings, descriptions, and gallery images
window._productsData = [
    // Audio & Electronics
    { 
        id: 'audio-1', category: 'Audio & Electronics', name: 'Wireless Earbuds Pro', 
        price: 14999, img: 'Assets/Products/Audio & Electronics/audio1.webp', 
        gallery: ['Assets/Products/Audio & Electronics/audio1.webp', 'Assets/Products/Audio & Electronics/audio2.webp', 'Assets/Products/Audio & Electronics/audio3.webp'],
        rating: 4.5, reviews: 342, 
        description: 'Premium wireless earbuds with active noise cancellation, 8-hour battery life, and crystal-clear sound quality. Perfect for music lovers and professionals on the go.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'audio-2', category: 'Audio & Electronics', name: 'Bluetooth Speaker Max', 
        price: 18999, img: 'Assets/Products/Audio & Electronics/audio2.webp', 
        gallery: ['Assets/Products/Audio & Electronics/audio2.webp', 'Assets/Products/Audio & Electronics/audio1.webp', 'Assets/Products/Audio & Electronics/audio4.webp'],
        rating: 4.7, reviews: 218,
        description: '360-degree portable Bluetooth speaker with deep bass, waterproof design, and 12-hour playtime. Take your music anywhere with this powerful sound companion.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'audio-3', category: 'Audio & Electronics', name: 'Noise Cancelling Headphones', 
        price: 20999, img: 'Assets/Products/Audio & Electronics/audio3.webp', 
        gallery: ['Assets/Products/Audio & Electronics/audio3.webp', 'Assets/Products/Audio & Electronics/audio1.webp', 'Assets/Products/Audio & Electronics/audio2.webp'],
        rating: 4.8, reviews: 567,
        description: 'Over-ear headphones with industry-leading noise cancellation, premium comfort padding, and studio-quality sound. Your personal audio sanctuary.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'audio-4', category: 'Audio & Electronics', name: 'Studio Condenser Microphone', 
        price: 18999, img: 'Assets/Products/Audio & Electronics/audio4.webp', 
        gallery: ['Assets/Products/Audio & Electronics/audio4.webp', 'Assets/Products/Audio & Electronics/audio5.webp', 'Assets/Products/Audio & Electronics/audio6.webp'],
        rating: 4.3, reviews: 89,
        description: 'Professional-grade condenser microphone kit with pop filter and shock mount. Ideal for podcasters, streamers, and content creators.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'audio-5', category: 'Audio & Electronics', name: 'Portable Voice Recorder', 
        price: 17999, img: 'Assets/Products/Audio & Electronics/audio5.webp', 
        gallery: ['Assets/Products/Audio & Electronics/audio5.webp', 'Assets/Products/Audio & Electronics/audio4.webp', 'Assets/Products/Audio & Electronics/audio6.webp'],
        rating: 4.1, reviews: 134,
        description: 'Compact digital voice recorder with HD audio capture, 32GB storage, and long battery life. Perfect for meetings, lectures, and interviews.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'audio-6', category: 'Audio & Electronics', name: 'USB Podcasting Mic', 
        price: 13999, img: 'Assets/Products/Audio & Electronics/audio6.webp',
        gallery: ['Assets/Products/Audio & Electronics/audio6.webp', 'Assets/Products/Audio & Electronics/audio4.webp', 'Assets/Products/Audio & Electronics/audio5.webp'],
        rating: 4.4, reviews: 276,
        description: 'Plug-and-play USB microphone with one-touch mute, volume control, and crystal-clear sound. Ready to use right out of the box.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'audio-7', category: 'Audio & Electronics', name: 'Gaming Headset RGB', 
        price: 15999, img: 'Assets/Products/Audio & Electronics/audio7.webp',
        gallery: ['Assets/Products/Audio & Electronics/audio7.webp', 'Assets/Products/Audio & Electronics/audio8.webp', 'Assets/Products/Audio & Electronics/audio3.webp'],
        rating: 4.6, reviews: 492,
        description: 'Immersive gaming headset with 7.1 surround sound, RGB lighting, and noise-canceling mic. Dominate your games with superior audio.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'audio-8', category: 'Audio & Electronics', name: 'Wireless Earphones Sport', 
        price: 19999, img: 'Assets/Products/Audio & Electronics/audio8.webp',
        gallery: ['Assets/Products/Audio & Electronics/audio8.webp', 'Assets/Products/Audio & Electronics/audio7.webp', 'Assets/Products/Audio & Electronics/audio1.webp'],
        rating: 4.2, reviews: 183,
        description: 'Sweat-resistant sport earphones with secure ear hooks, powerful bass, and 10-hour battery. Your ultimate workout companion.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'audio-9', category: 'Audio & Electronics', name: 'Premium Hi-Fi Amplifier', 
        price: 78000, img: 'Assets/Products/Audio & Electronics/audio9.webp',
        gallery: ['Assets/Products/Audio & Electronics/audio9.webp', 'Assets/Products/Audio & Electronics/audio3.webp', 'Assets/Products/Audio & Electronics/audio2.webp'],
        rating: 4.9, reviews: 47,
        description: 'Reference-grade stereo amplifier with Class-A/B topology, 100W per channel, and audiophile components throughout. For the discerning listener.',
        inStock: false, fastDelivery: false 
    },
    // Bags
    { 
        id: 'bags-1', category: 'bags', name: 'Structured Leather Tote', 
        price: 780, img: 'Assets/Products/bags/bag1.avif',
        gallery: ['Assets/Products/bags/bag1.avif', 'Assets/Products/bags/bag2.avif', 'Assets/Products/bags/bag3.avif'],
        rating: 4.6, reviews: 321,
        description: 'Spacious structured tote crafted from premium vegan leather. Features inner pockets, magnetic closure, and reinforced handles for everyday elegance.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-2', category: 'bags', name: 'Crossbody Sling Bag', 
        price: 680, img: 'Assets/Products/bags/bag2.avif',
        gallery: ['Assets/Products/bags/bag2.avif', 'Assets/Products/bags/bag1.avif', 'Assets/Products/bags/bag4.avif'],
        rating: 4.4, reviews: 198,
        description: 'Compact crossbody sling with adjustable strap, anti-scratch lining, and multiple compartments. Ideal for daily essentials on the move.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'bags-3', category: 'bags', name: 'Mini Canvas Backpack', 
        price: 1289, img: 'Assets/Products/bags/bag3.avif',
        gallery: ['Assets/Products/bags/bag3.avif', 'Assets/Products/bags/bag5.avif', 'Assets/Products/bags/bag6.avif'],
        rating: 4.7, reviews: 412,
        description: 'Chic mini backpack in durable canvas with leather trims. Holds a 13-inch laptop, has padded straps, and looks stunning with any outfit.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-4', category: 'bags', name: 'Woven Straw Tote', 
        price: 389, img: 'Assets/Products/bags/bag4.avif',
        gallery: ['Assets/Products/bags/bag4.avif', 'Assets/Products/bags/bag1.avif', 'Assets/Products/bags/bag7.avif'],
        rating: 4.3, reviews: 156,
        description: 'Handcrafted straw tote bag with vegan leather handles and removable inner pouch. Your perfect beach and summer companion.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-5', category: 'bags', name: 'Quilted Chain Shoulder Bag', 
        price: 299, img: 'Assets/Products/bags/bag5.avif',
        gallery: ['Assets/Products/bags/bag5.avif', 'Assets/Products/bags/bag6.avif', 'Assets/Products/bags/bag8.avif'],
        rating: 4.5, reviews: 287,
        description: 'Timeless quilted shoulder bag with gold-tone chain strap. Fits phone, cards, and essentials. Available in multiple colors.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-6', category: 'bags', name: 'Leather Hobo Bag', 
        price: 520, img: 'Assets/Products/bags/bag6.avif',
        gallery: ['Assets/Products/bags/bag6.avif', 'Assets/Products/bags/bag5.avif', 'Assets/Products/bags/bag9.avif'],
        rating: 4.2, reviews: 174,
        description: 'Soft slouchy hobo bag in genuine leather with suede lining. Effortlessly stylish for both day and evening wear.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-7', category: 'bags', name: 'Flap Envelope Clutch', 
        price: 810, img: 'Assets/Products/bags/bag7.avif',
        gallery: ['Assets/Products/bags/bag7.avif', 'Assets/Products/bags/bag8.avif', 'Assets/Products/bags/bag5.avif'],
        rating: 4.6, reviews: 229,
        description: 'Elegant envelope clutch with flap closure and detachable wrist strap. Perfect for evenings, parties, and formal occasions.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-8', category: 'bags', name: 'Bucket Bag with Drawstring', 
        price: 725, img: 'Assets/Products/bags/bag8.avif',
        gallery: ['Assets/Products/bags/bag8.avif', 'Assets/Products/bags/bag7.avif', 'Assets/Products/bags/bag10.avif'],
        rating: 4.4, reviews: 302,
        description: 'Trendy bucket bag with drawstring top and detachable shoulder strap. Roomy interior with a slip pocket keeps your essentials organized.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-9', category: 'bags', name: 'Satchel Work Bag', 
        price: 1020, img: 'Assets/Products/bags/bag9.avif',
        gallery: ['Assets/Products/bags/bag9.avif', 'Assets/Products/bags/bag3.avif', 'Assets/Products/bags/bag10.avif'],
        rating: 4.8, reviews: 187,
        description: 'Professional satchel with dedicated laptop compartment, organizer pockets, and sturdy top handles. Transitions seamlessly from office to weekend.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'bags-10', category: 'bags', name: 'Compact Belt Bag', 
        price: 600, img: 'Assets/Products/bags/bag10.avif',
        gallery: ['Assets/Products/bags/bag10.avif', 'Assets/Products/bags/bag2.avif', 'Assets/Products/bags/bag5.avif'],
        rating: 4.3, reviews: 241,
        description: 'Hands-free belt bag with adjustable strap and zip pockets. Wear it around the waist or crossbody — perfect for festivals and travel.',
        inStock: true, fastDelivery: true 
    },
    // Beauty & Fragrance
    { 
        id: 'beauty-1', category: 'Beauty & Fragrance', name: 'Glow Skincare Set', 
        price: 238, img: 'Assets/Products/Beauty & Fragrance/fregrance1.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance1.avif', 'Assets/Products/Beauty & Fragrance/fregrance2.avif', 'Assets/Products/Beauty & Fragrance/fragrance7.avif'],
        rating: 4.6, reviews: 389,
        description: 'Complete skincare ritual set with hydrating serum, toning mist, and nourishing moisturizer. Formulated with natural botanicals for a radiant glow.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-2', category: 'Beauty & Fragrance', name: 'Soy Wax Luxury Candle', 
        price: 333, img: 'Assets/Products/Beauty & Fragrance/fregrance2.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance2.avif', 'Assets/Products/Beauty & Fragrance/fregrance3.avif', 'Assets/Products/Beauty & Fragrance/fragrance8.avif'],
        rating: 4.8, reviews: 524,
        description: 'Hand-poured soy wax candle with cotton wick and premium fragrance oils. Burns for up to 50 hours with a warm, ambient glow.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-3', category: 'Beauty & Fragrance', name: 'Layered Amber Perfume', 
        price: 589, img: 'Assets/Products/Beauty & Fragrance/fregrance3.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance3.avif', 'Assets/Products/Beauty & Fragrance/fregrance4.webp', 'Assets/Products/Beauty & Fragrance/fragrance9.avif'],
        rating: 4.7, reviews: 312,
        description: 'Rich oriental fragrance with top notes of bergamot, heart of jasmine, and base of amber and sandalwood. A scent that lingers beautifully.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-4', category: 'Beauty & Fragrance', name: 'Floral Eau de Parfum', 
        price: 271, img: 'Assets/Products/Beauty & Fragrance/fregrance4.webp',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance4.webp', 'Assets/Products/Beauty & Fragrance/fregrance3.avif', 'Assets/Products/Beauty & Fragrance/fragrance10.avif'],
        rating: 4.5, reviews: 478,
        description: 'Delicate floral eau de parfum bursting with rose, peony, and white musk. A timeless feminine fragrance for every occasion.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'beauty-5', category: 'Beauty & Fragrance', name: 'Rose Body Butter', 
        price: 442, img: 'Assets/Products/Beauty & Fragrance/fregrance5.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance5.avif', 'Assets/Products/Beauty & Fragrance/fregrance1.avif', 'Assets/Products/Beauty & Fragrance/fregrance6.avif'],
        rating: 4.4, reviews: 263,
        description: 'Intensely nourishing rose-infused body butter with shea butter and vitamin E. Melts into skin for long-lasting hydration and a subtle floral scent.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-6', category: 'Beauty & Fragrance', name: 'Jasmine Bath Salts', 
        price: 221, img: 'Assets/Products/Beauty & Fragrance/fregrance6.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fregrance6.avif', 'Assets/Products/Beauty & Fragrance/fregrance5.avif', 'Assets/Products/Beauty & Fragrance/fragrance7.avif'],
        rating: 4.3, reviews: 149,
        description: 'Mineral-rich bath salts infused with jasmine essential oil and dried petals. Transform your bath into a luxurious spa experience.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-7', category: 'Beauty & Fragrance', name: 'Oud Wood Perfume', 
        price: 150, img: 'Assets/Products/Beauty & Fragrance/fragrance7.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fragrance7.avif', 'Assets/Products/Beauty & Fragrance/fregrance3.avif', 'Assets/Products/Beauty & Fragrance/fragrance9.avif'],
        rating: 4.6, reviews: 198,
        description: 'Opulent oud wood fragrance with smoky, woody undertones and a hint of vanilla. A bold, sophisticated scent for confident souls.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-8', category: 'Beauty & Fragrance', name: 'Vitamin C Brightening Serum', 
        price: 320, img: 'Assets/Products/Beauty & Fragrance/fragrance8.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fragrance8.avif', 'Assets/Products/Beauty & Fragrance/fregrance1.avif', 'Assets/Products/Beauty & Fragrance/fragrance9.avif'],
        rating: 4.7, reviews: 601,
        description: '20% vitamin C serum with hyaluronic acid and niacinamide. Visibly brightens, evens skin tone, and reduces fine lines with regular use.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'beauty-9', category: 'Beauty & Fragrance', name: 'Lavender Reed Diffuser', 
        price: 188, img: 'Assets/Products/Beauty & Fragrance/fragrance10.avif',
        gallery: ['Assets/Products/Beauty & Fragrance/fragrance10.avif', 'Assets/Products/Beauty & Fragrance/fregrance2.avif', 'Assets/Products/Beauty & Fragrance/fragrance9.avif'],
        rating: 4.2, reviews: 117,
        description: 'Natural lavender reed diffuser with 200ml of premium fragrance oil and 10 rattan reeds. Fill your space with calming, soothing aromas.',
        inStock: false, fastDelivery: false 
    },
    // Books
    { 
        id: 'books-1', category: 'Books', name: 'The Design Thinking Guide', 
        price: 122, img: 'Assets/Products/Books/books1.webp',
        gallery: ['Assets/Products/Books/books1.webp', 'Assets/Products/Books/book2.webp', 'Assets/Products/Books/book3.webp'],
        rating: 4.7, reviews: 284,
        description: 'A comprehensive guide to design thinking methodology with real-world case studies and practical frameworks. Essential reading for innovators and product designers.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'books-2', category: 'Books', name: 'The Creative Journal', 
        price: 118, img: 'Assets/Products/Books/book2.webp',
        gallery: ['Assets/Products/Books/book2.webp', 'Assets/Products/Books/books1.webp', 'Assets/Products/Books/book4.webp'],
        rating: 4.5, reviews: 367,
        description: 'Beautifully crafted journal with prompts, blank pages, and illustration spaces to fuel your creativity. Premium paper, lay-flat binding.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'books-3', category: 'Books', name: 'Mindfulness for Modern Life', 
        price: 131, img: 'Assets/Products/Books/book3.webp',
        gallery: ['Assets/Products/Books/book3.webp', 'Assets/Products/Books/book2.webp', 'Assets/Products/Books/book5.webp'],
        rating: 4.6, reviews: 445,
        description: 'A practical guide to mindfulness and meditation for today\'s fast-paced world. 30-day program with guided exercises and habit trackers.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'books-4', category: 'Books', name: 'The Art of Storytelling', 
        price: 199, img: 'Assets/Products/Books/book4.webp',
        gallery: ['Assets/Products/Books/book4.webp', 'Assets/Products/Books/book3.webp', 'Assets/Products/Books/book6.webp'],
        rating: 4.8, reviews: 193,
        description: 'Master the craft of storytelling across mediums — writing, film, photography, and design. Packed with examples from iconic works.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'books-5', category: 'Books', name: 'Financial Freedom Blueprint', 
        price: 188, img: 'Assets/Products/Books/book5.webp',
        gallery: ['Assets/Products/Books/book5.webp', 'Assets/Products/Books/book4.webp', 'Assets/Products/Books/book7.webp'],
        rating: 4.4, reviews: 512,
        description: 'Step-by-step financial planning guide covering budgeting, investing, and building wealth. Written for everyday people, not just finance experts.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'books-6', category: 'Books', name: 'Travel the World: 100 Journeys', 
        price: 172, img: 'Assets/Products/Books/book6.webp',
        gallery: ['Assets/Products/Books/book6.webp', 'Assets/Products/Books/book5.webp', 'Assets/Products/Books/books1.webp'],
        rating: 4.3, reviews: 228,
        description: 'Stunning photo book featuring 100 must-do journeys across all seven continents. Lush imagery and inspiring stories from seasoned travelers.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'books-7', category: 'Books', name: 'Modern Architecture Digest', 
        price: 145, img: 'Assets/Products/Books/book7.webp',
        gallery: ['Assets/Products/Books/book7.webp', 'Assets/Products/Books/book6.webp', 'Assets/Products/Books/books1.webp'],
        rating: 4.5, reviews: 161,
        description: 'Coffee table book showcasing iconic modern architecture worldwide. Features floor plans, interviews with architects, and breathtaking photography.',
        inStock: true, fastDelivery: false 
    },
    // Fashion
    { 
        id: 'fashion-1', category: 'Fashion', name: 'Soft Knit Cashmere Sweater', 
        price: 1350, img: 'Assets/Products/Fashion/fashion1.avif',
        gallery: ['Assets/Products/Fashion/fashion1.avif', 'Assets/Products/Fashion/fashion2.avif', 'Assets/Products/Fashion/fashion3.avif'],
        rating: 4.8, reviews: 432,
        description: 'Ultra-soft cashmere blend sweater in a relaxed oversized fit. Ribbed cuffs, hem, and collar. Available in neutral and seasonal shades.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'fashion-2', category: 'Fashion', name: 'Single-Breasted Blazer', 
        price: 1100, img: 'Assets/Products/Fashion/fashion2.avif',
        gallery: ['Assets/Products/Fashion/fashion2.avif', 'Assets/Products/Fashion/fashion1.avif', 'Assets/Products/Fashion/fashion4.avif'],
        rating: 4.6, reviews: 287,
        description: 'Sharp single-breasted blazer in premium stretch fabric. Structured shoulders, flap pockets, and a flattering silhouette. Office to evening effortlessly.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-3', category: 'Fashion', name: 'Floral Wrap Midi Dress', 
        price: 268, img: 'Assets/Products/Fashion/fashion3.avif',
        gallery: ['Assets/Products/Fashion/fashion3.avif', 'Assets/Products/Fashion/fashion5.avif', 'Assets/Products/Fashion/fashion6.avif'],
        rating: 4.5, reviews: 356,
        description: 'Elegant wrap midi dress in a soft floral print. V-neckline, adjustable waist tie, and flowing fabric. Perfect for brunches, dates, and garden parties.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-4', category: 'Fashion', name: 'Linen Button-Down Shirt', 
        price: 169, img: 'Assets/Products/Fashion/fashion4.avif',
        gallery: ['Assets/Products/Fashion/fashion4.avif', 'Assets/Products/Fashion/fashion3.avif', 'Assets/Products/Fashion/fashion7.avif'],
        rating: 4.3, reviews: 198,
        description: 'Breathable linen shirt with relaxed fit and roll-up sleeves. A warm-weather essential that pairs with everything from denim to trousers.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'fashion-5', category: 'Fashion', name: 'High-Waist Tailored Trousers', 
        price: 310, img: 'Assets/Products/Fashion/fashion5.avif',
        gallery: ['Assets/Products/Fashion/fashion5.avif', 'Assets/Products/Fashion/fashion2.avif', 'Assets/Products/Fashion/fashion8.webp'],
        rating: 4.7, reviews: 412,
        description: 'High-waist wide-leg trousers in a crepe fabric. Side pockets, zip fly, and a clean, polished look. Works for both formal and smart-casual settings.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-6', category: 'Fashion', name: 'Puff-Sleeve Mini Dress', 
        price: 390, img: 'Assets/Products/Fashion/fashion6.avif',
        gallery: ['Assets/Products/Fashion/fashion6.avif', 'Assets/Products/Fashion/fashion3.avif', 'Assets/Products/Fashion/fashion9.avif'],
        rating: 4.4, reviews: 267,
        description: 'Playful puff-sleeve mini dress with smocked waistband and zip back. Light, airy fabric perfect for summer celebrations.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-7', category: 'Fashion', name: 'Embroidered Silk Kurta', 
        price: 2599, img: 'Assets/Products/Fashion/fashion7.avif',
        gallery: ['Assets/Products/Fashion/fashion7.avif', 'Assets/Products/Fashion/fashion9.avif', 'Assets/Products/Fashion/fashion1.avif'],
        rating: 4.9, reviews: 341,
        description: 'Luxurious silk kurta with hand-embroidered neckline and hem. Straight cut, side slits, and breathable silk lining. Wear alone or with palazzo pants.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-8', category: 'Fashion', name: 'Denim Jacket Oversized', 
        price: 420, img: 'Assets/Products/Fashion/fashion8.webp',
        gallery: ['Assets/Products/Fashion/fashion8.webp', 'Assets/Products/Fashion/fashion4.avif', 'Assets/Products/Fashion/fashion5.avif'],
        rating: 4.5, reviews: 389,
        description: 'Classic oversized denim jacket with distressed details, chest pockets, and a lived-in wash. Layer over everything — the ultimate wardrobe staple.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'fashion-9', category: 'Fashion', name: 'Anarkali Suit Set', 
        price: 2199, img: 'Assets/Products/Fashion/fashion9.avif',
        gallery: ['Assets/Products/Fashion/fashion9.avif', 'Assets/Products/Fashion/fashion7.avif', 'Assets/Products/Fashion/fashion6.avif'],
        rating: 4.8, reviews: 276,
        description: 'Graceful anarkali suit set with georgette flare, matching dupatta, and embellished hem. Perfect for festive occasions and celebrations.',
        inStock: true, fastDelivery: false 
    },
    // Footwear
    { 
        id: 'footwear-1', category: 'Footwear', name: 'Clean Minimal Sneakers', 
        price: 899, img: 'Assets/Products/Footwear/footwear1.avif',
        gallery: ['Assets/Products/Footwear/footwear1.avif', 'Assets/Products/Footwear/footwear2.avif', 'Assets/Products/Footwear/footwear3.avif'],
        rating: 4.6, reviews: 587,
        description: 'Minimalist low-top sneakers in premium canvas with a vulcanized rubber sole. Clean silhouette that pairs with every outfit, every day.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'footwear-2', category: 'Footwear', name: 'Italian Leather Loafers', 
        price: 1200, img: 'Assets/Products/Footwear/footwear2.avif',
        gallery: ['Assets/Products/Footwear/footwear2.avif', 'Assets/Products/Footwear/footwear1.avif', 'Assets/Products/Footwear/footwear4.avif'],
        rating: 4.7, reviews: 342,
        description: 'Handcrafted Italian leather loafers with penny strap and leather lining. Timeless style with all-day comfort for the modern professional.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-3', category: 'Footwear', name: 'Block Heel Ankle Boots', 
        price: 2200, img: 'Assets/Products/Footwear/footwear3.avif',
        gallery: ['Assets/Products/Footwear/footwear3.avif', 'Assets/Products/Footwear/footwear2.avif', 'Assets/Products/Footwear/footwear5.avif'],
        rating: 4.8, reviews: 254,
        description: 'Sleek ankle boots with a stable block heel, side zip, and cushioned insole. Elevates any outfit without compromising on comfort.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-4', category: 'Footwear', name: 'Strappy Flat Sandals', 
        price: 1200, img: 'Assets/Products/Footwear/footwear4.avif',
        gallery: ['Assets/Products/Footwear/footwear4.avif', 'Assets/Products/Footwear/footwear3.avif', 'Assets/Products/Footwear/footwear6.avif'],
        rating: 4.4, reviews: 318,
        description: 'Dainty strappy flat sandals with adjustable ankle buckle and padded footbed. Light, airy, and perfect for warm days.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-5', category: 'Footwear', name: 'Running Shoes Lightweight', 
        price: 1567, img: 'Assets/Products/Footwear/footwear5.avif',
        gallery: ['Assets/Products/Footwear/footwear5.avif', 'Assets/Products/Footwear/footwear1.avif', 'Assets/Products/Footwear/footwear7.avif'],
        rating: 4.5, reviews: 489,
        description: 'Lightweight performance running shoes with reactive foam midsole, breathable mesh upper, and durable rubber outsole. Built for speed and endurance.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-6', category: 'Footwear', name: 'Espadrille Wedge Sandals', 
        price: 722, img: 'Assets/Products/Footwear/footwear6.avif',
        gallery: ['Assets/Products/Footwear/footwear6.avif', 'Assets/Products/Footwear/footwear4.avif', 'Assets/Products/Footwear/footwear8.avif'],
        rating: 4.3, reviews: 173,
        description: 'Chic espadrille wedge sandals with a braided jute midsole and ankle tie. Adds height comfortably — perfect for summer dressing.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-7', category: 'Footwear', name: 'Oxford Brogues Classic', 
        price: 516, img: 'Assets/Products/Footwear/footwear7.avif',
        gallery: ['Assets/Products/Footwear/footwear7.avif', 'Assets/Products/Footwear/footwear2.avif', 'Assets/Products/Footwear/footwear9.avif'],
        rating: 4.6, reviews: 228,
        description: 'Handsome oxford brogues with classic wingtip detailing, lace-up closure, and leather sole. From boardroom to weekend brunch.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-8', category: 'Footwear', name: 'Platform Chelsea Boots', 
        price: 999, img: 'Assets/Products/Footwear/footwear8.avif',
        gallery: ['Assets/Products/Footwear/footwear8.avif', 'Assets/Products/Footwear/footwear3.avif', 'Assets/Products/Footwear/footwear7.avif'],
        rating: 4.7, reviews: 396,
        description: 'Bold platform chelsea boots with elastic side panels and pull-tab. Chunky rubber sole adds statement height. Pairs with everything from jeans to dresses.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'footwear-9', category: 'Footwear', name: 'Embellished Heeled Mules', 
        price: 1298, img: 'Assets/Products/Footwear/footwear9.avif',
        gallery: ['Assets/Products/Footwear/footwear9.avif', 'Assets/Products/Footwear/footwear6.avif', 'Assets/Products/Footwear/footwear4.avif'],
        rating: 4.5, reviews: 204,
        description: 'Glamorous heeled mules with embellished toe strap and kitten heel. Dress up any look instantly — from office to evening parties.',
        inStock: true, fastDelivery: false 
    },
    // Gadgets
    { 
        id: 'gadgets-1', category: 'Gadgets', name: 'Premium Smartwatch Series X', 
        price: 92000, img: 'Assets/Products/Gadgets/gadgets1.avif',
        gallery: ['Assets/Products/Gadgets/gadgets1.avif', 'Assets/Products/Gadgets/gadgets2.webp', 'Assets/Products/Gadgets/gadgets3.webp'],
        rating: 4.9, reviews: 276,
        description: 'Flagship smartwatch with AMOLED display, ECG monitoring, GPS, and 5-day battery life. Sapphire crystal glass and titanium case for premium durability.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'gadgets-2', category: 'Gadgets', name: 'Fitness Tracker Band', 
        price: 232, img: 'Assets/Products/Gadgets/gadgets2.webp',
        gallery: ['Assets/Products/Gadgets/gadgets2.webp', 'Assets/Products/Gadgets/gadgets1.avif', 'Assets/Products/Gadgets/gadgets4.webp'],
        rating: 4.4, reviews: 534,
        description: 'Slim fitness tracker with heart rate monitor, sleep tracking, and step counting. 14-day battery and water-resistant design. Your daily health companion.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'gadgets-3', category: 'Gadgets', name: 'Foldable Mini Drone', 
        price: 186, img: 'Assets/Products/Gadgets/gadgets3.webp',
        gallery: ['Assets/Products/Gadgets/gadgets3.webp', 'Assets/Products/Gadgets/gadgets4.webp', 'Assets/Products/Gadgets/gadgets5.webp'],
        rating: 4.2, reviews: 167,
        description: 'Compact foldable drone with 4K camera, one-tap liftoff, and gesture control. Fits in your pocket — capture aerial shots anywhere, anytime.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'gadgets-4', category: 'Gadgets', name: 'Wireless Charging Pad Trio', 
        price: 285, img: 'Assets/Products/Gadgets/gadgets4.webp',
        gallery: ['Assets/Products/Gadgets/gadgets4.webp', 'Assets/Products/Gadgets/gadgets2.webp', 'Assets/Products/Gadgets/gadgets5.webp'],
        rating: 4.5, reviews: 423,
        description: 'Three-in-one wireless charging station that simultaneously charges phone, earbuds, and smartwatch. Fast-charge compatible and clutter-free.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'gadgets-5', category: 'Gadgets', name: 'Smart LED Desk Lamp', 
        price: 266, img: 'Assets/Products/Gadgets/gadgets5.webp',
        gallery: ['Assets/Products/Gadgets/gadgets5.webp', 'Assets/Products/Gadgets/gadgets4.webp', 'Assets/Products/Gadgets/gadgets6.webp'],
        rating: 4.3, reviews: 298,
        description: 'Touch-control desk lamp with adjustable color temperature (2700K–6500K), dimming, and built-in USB charging port. Flicker-free LED eye protection.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'gadgets-6', category: 'Gadgets', name: 'Portable Power Bank 20000mAh', 
        price: 199, img: 'Assets/Products/Gadgets/gadgets6.webp',
        gallery: ['Assets/Products/Gadgets/gadgets6.webp', 'Assets/Products/Gadgets/gadgets5.webp', 'Assets/Products/Gadgets/gadgets2.webp'],
        rating: 4.6, reviews: 712,
        description: 'High-capacity 20000mAh power bank with 65W fast charging, dual USB-A, USB-C output, and LED indicator. Charges laptop, phone, and tablet simultaneously.',
        inStock: false, fastDelivery: false 
    },
    // Jewelry
    { 
        id: 'jewelry-1', category: 'Jewelry', name: 'Freshwater Pearl Pendant', 
        price: 6543, img: 'Assets/Products/Jewelry/jewelry1.avif',
        gallery: ['Assets/Products/Jewelry/jewelry1.avif', 'Assets/Products/Jewelry/Jewelry2.avif', 'Assets/Products/Jewelry/jewelry5.avif'],
        rating: 4.8, reviews: 189,
        description: 'Lustrous freshwater pearl pendant on a sterling silver chain. Handpicked pearls with natural iridescence. A timeless gift for her.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'jewelry-2', category: 'Jewelry', name: 'Gold Hoop Earrings Large', 
        price: 1300, img: 'Assets/Products/Jewelry/Jewelry2.avif',
        gallery: ['Assets/Products/Jewelry/Jewelry2.avif', 'Assets/Products/Jewelry/jewelry1.avif', 'Assets/Products/Jewelry/Jewelry3.avif'],
        rating: 4.6, reviews: 324,
        description: 'Statement large gold hoop earrings in 18K gold plating over sterling silver. Lightweight hinged closure. Everyday glam made effortless.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-3', category: 'Jewelry', name: 'Floral Diamond Ring', 
        price: 2350, img: 'Assets/Products/Jewelry/Jewelry3.avif',
        gallery: ['Assets/Products/Jewelry/Jewelry3.avif', 'Assets/Products/Jewelry/Jewelry4.avif', 'Assets/Products/Jewelry/jewelry5.avif'],
        rating: 4.7, reviews: 241,
        description: 'Delicate floral ring set with lab-grown diamonds in a 14K gold band. Petal cluster design that catches the light beautifully.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-4', category: 'Jewelry', name: 'Layered Chain Necklace Set', 
        price: 2199, img: 'Assets/Products/Jewelry/Jewelry4.avif',
        gallery: ['Assets/Products/Jewelry/Jewelry4.avif', 'Assets/Products/Jewelry/jewelry1.avif', 'Assets/Products/Jewelry/jewelry6.avif'],
        rating: 4.5, reviews: 378,
        description: 'Set of three layered chain necklaces at varied lengths with delicate charms. Mix-and-match styling in gold or silver tones.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-5', category: 'Jewelry', name: 'Gemstone Cocktail Ring', 
        price: 4125, img: 'Assets/Products/Jewelry/jewelry5.avif',
        gallery: ['Assets/Products/Jewelry/jewelry5.avif', 'Assets/Products/Jewelry/Jewelry3.avif', 'Assets/Products/Jewelry/jewelry7.avif'],
        rating: 4.8, reviews: 132,
        description: 'Bold cocktail ring featuring a cushion-cut gemstone in a pavé-set halo. Available in amethyst, sapphire, and emerald. A conversation starter.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-6', category: 'Jewelry', name: 'Bangle Bracelet Set', 
        price: 3339, img: 'Assets/Products/Jewelry/jewelry6.avif',
        gallery: ['Assets/Products/Jewelry/jewelry6.avif', 'Assets/Products/Jewelry/Jewelry4.avif', 'Assets/Products/Jewelry/jewelry8.avif'],
        rating: 4.4, reviews: 267,
        description: 'Set of six mixed-finish bangles — polished, matte, and hammered. Stack them all or wear individually for varied looks.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-7', category: 'Jewelry', name: 'Tassel Drop Earrings', 
        price: 5120, img: 'Assets/Products/Jewelry/jewelry7.avif',
        gallery: ['Assets/Products/Jewelry/jewelry7.avif', 'Assets/Products/Jewelry/Jewelry2.avif', 'Assets/Products/Jewelry/jewelry9.avif'],
        rating: 4.6, reviews: 198,
        description: 'Long tassel drop earrings with faceted crystal accents and gold-plated hooks. Adds dramatic flair to evening wear and formal occasions.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-8', category: 'Jewelry', name: 'Diamond Tennis Bracelet', 
        price: 9998, img: 'Assets/Products/Jewelry/jewelry8.avif',
        gallery: ['Assets/Products/Jewelry/jewelry8.avif', 'Assets/Products/Jewelry/jewelry5.avif', 'Assets/Products/Jewelry/jewelry10.avif'],
        rating: 4.9, reviews: 87,
        description: 'Classic diamond tennis bracelet with channel-set lab-grown diamonds in 14K white gold. A heritage piece that transcends trends.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-9', category: 'Jewelry', name: 'Minimalist Stud Earrings', 
        price: 1250, img: 'Assets/Products/Jewelry/jewelry9.avif',
        gallery: ['Assets/Products/Jewelry/jewelry9.avif', 'Assets/Products/Jewelry/Jewelry2.avif', 'Assets/Products/Jewelry/jewelry6.avif'],
        rating: 4.5, reviews: 412,
        description: 'Simple, elegant stud earrings in sterling silver with a bezel-set cubic zirconia. Hypoallergenic posts. Wear them every day, everywhere.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'jewelry-10', category: 'Jewelry', name: 'Vintage Charm Necklace', 
        price: 3450, img: 'Assets/Products/Jewelry/jewelry10.avif',
        gallery: ['Assets/Products/Jewelry/jewelry10.avif', 'Assets/Products/Jewelry/jewelry1.avif', 'Assets/Products/Jewelry/Jewelry4.avif'],
        rating: 4.7, reviews: 176,
        description: 'Antique-finish charm necklace with hand-enameled pendants on a delicate chain. Each charm tells a story — collect your favorites.',
        inStock: true, fastDelivery: false 
    },
    // Watches
    { 
        id: 'watches-1', category: 'Watches', name: 'Heritage Chronograph Watch', 
        price: 2189, img: 'Assets/Products/Watches/watches1.avif',
        gallery: ['Assets/Products/Watches/watches1.avif', 'Assets/Products/Watches/watches2.avif', 'Assets/Products/Watches/watches3.avif'],
        rating: 4.8, reviews: 312,
        description: 'Swiss-inspired heritage chronograph with stainless steel case, sapphire crystal, and a genuine leather strap. Timekeeping as an art form.',
        inStock: true, fastDelivery: true 
    },
    { 
        id: 'watches-2', category: 'Watches', name: 'Slim Sport Titanium Watch', 
        price: 1232, img: 'Assets/Products/Watches/watches2.avif',
        gallery: ['Assets/Products/Watches/watches2.avif', 'Assets/Products/Watches/watches1.avif', 'Assets/Products/Watches/watches4.avif'],
        rating: 4.5, reviews: 276,
        description: 'Lightweight titanium sport watch with 100m water resistance, luminous hands, and silicone sport band. Engineered for active lifestyles.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-3', category: 'Watches', name: 'Rose Gold Dress Watch', 
        price: 1122, img: 'Assets/Products/Watches/watches3.avif',
        gallery: ['Assets/Products/Watches/watches3.avif', 'Assets/Products/Watches/watches2.avif', 'Assets/Products/Watches/watches5.avif'],
        rating: 4.6, reviews: 341,
        description: 'Elegant rose gold dress watch with a sunburst dial, Roman numeral indices, and a mesh bracelet. Sophisticated and feminine.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-4', category: 'Watches', name: 'Skeleton Automatic Watch', 
        price: 1489, img: 'Assets/Products/Watches/watches4.avif',
        gallery: ['Assets/Products/Watches/watches4.avif', 'Assets/Products/Watches/watches3.avif', 'Assets/Products/Watches/watches6.avif'],
        rating: 4.7, reviews: 198,
        description: 'Mechanical skeleton watch with exhibition case back, automatic movement, and genuine leather strap. A marvel of watchmaking craftsmanship.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-5', category: 'Watches', name: 'Casual Canvas Strap Watch', 
        price: 699, img: 'Assets/Products/Watches/watches5.avif',
        gallery: ['Assets/Products/Watches/watches5.avif', 'Assets/Products/Watches/watches2.avif', 'Assets/Products/Watches/watches7.avif'],
        rating: 4.3, reviews: 423,
        description: 'Laid-back casual watch with a canvas NATO strap and clean Arabic dial. Quartz movement with 50m water resistance. Easy, everyday style.',
        inStock: false, fastDelivery: false 
    },
    { 
        id: 'watches-6', category: 'Watches', name: 'Pilot\'s GMT Watch', 
        price: 2349, img: 'Assets/Products/Watches/watches6.avif',
        gallery: ['Assets/Products/Watches/watches6.avif', 'Assets/Products/Watches/watches4.avif', 'Assets/Products/Watches/watches8.avif'],
        rating: 4.8, reviews: 167,
        description: 'Pilot-inspired GMT watch with dual time zone function, large Arabic numerals, and a rotating 24-hour bezel. For the traveller in you.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-7', category: 'Watches', name: 'Luxury Tourbillon Watch', 
        price: 3999, img: 'Assets/Products/Watches/watches7.avif',
        gallery: ['Assets/Products/Watches/watches7.avif', 'Assets/Products/Watches/watches6.avif', 'Assets/Products/Watches/watches9.avif'],
        rating: 4.9, reviews: 54,
        description: 'Fine complications tourbillon with a hand-wound movement, exhibition caseback, and alligator leather strap. Haute horlogerie at its finest.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-8', category: 'Watches', name: 'Diver\'s 200m Watch', 
        price: 3219, img: 'Assets/Products/Watches/watches8.avif',
        gallery: ['Assets/Products/Watches/watches8.avif', 'Assets/Products/Watches/watches2.avif', 'Assets/Products/Watches/watches10.avif'],
        rating: 4.7, reviews: 289,
        description: 'ISO 6425-rated dive watch with 200m water resistance, unidirectional bezel, luminous indices, and screw-down crown. Built for the deep.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-9', category: 'Watches', name: 'Minimalist Field Watch', 
        price: 2679, img: 'Assets/Products/Watches/watches9.avif',
        gallery: ['Assets/Products/Watches/watches9.avif', 'Assets/Products/Watches/watches5.avif', 'Assets/Products/Watches/watches3.avif'],
        rating: 4.6, reviews: 334,
        description: 'Pared-back field watch with a matte dial, 12-hour scale, lume-painted hands, and a military-style canvas strap. Form follows function.',
        inStock: true, fastDelivery: false 
    },
    { 
        id: 'watches-10', category: 'Watches', name: 'Smartwatch Pro Edition', 
        price: 4499, img: 'Assets/Products/Watches/watches10.avif',
        gallery: ['Assets/Products/Watches/watches10.avif', 'Assets/Products/Watches/watches7.avif', 'Assets/Products/Watches/watches8.avif'],
        rating: 4.8, reviews: 412,
        description: 'Luxury smartwatch with AMOLED Always-On Display, premium stainless steel case, and interchangeable straps. Blends tradition with technology.',
        inStock: false, fastDelivery: false 
    },
];

// Assign unique keys to each product
window._productsData.forEach((product, index) => {
    product.key = `${product.id}-${index}`;
});

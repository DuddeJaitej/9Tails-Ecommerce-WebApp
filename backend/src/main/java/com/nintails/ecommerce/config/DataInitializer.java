package com.nintails.ecommerce.config;

import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.repository.CategoryRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds DB on first run only (skips if categories already exist).
 * Each product has ONE image — the exact file that exists in Assets/Products/.
 * No gallery arrays — single image per product as requested.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository  productRepository;
    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Database already seeded — skipping.");
            return;
        }

        log.info("Seeding database...");

        // ── Categories ────────────────────────────────────────────────────────
        Category fashion  = save(cat("Fashion",           "Clothing and apparel",             "Assets/Products/Fashion/fashion1.avif"));
        Category jewelry  = save(cat("Jewelry",           "Rings, necklaces and bracelets",   "Assets/Products/Jewelry/jewelry1.avif"));
        Category watches  = save(cat("Watches",           "Luxury and casual watches",        "Assets/Products/Watches/watches1.avif"));
        Category bags     = save(cat("Bags",              "Handbags, backpacks and travel",   "Assets/Products/bags/bag1.avif"));
        Category footwear = save(cat("Footwear",          "Shoes, sandals and boots",         "Assets/Products/Footwear/footwear1.avif"));
        Category beauty   = save(cat("Beauty & Fragrance","Perfumes and skincare",            "Assets/Products/Beauty & Fragrance/fregrance1.avif"));
        Category audio    = save(cat("Audio & Electronics","Headphones and speakers",         "Assets/Products/Audio & Electronics/audio1.webp"));
        Category gadgets  = save(cat("Gadgets",           "Smart devices and accessories",    "Assets/Products/Gadgets/gadgets4.webp"));
        Category books    = save(cat("Books",             "Fiction and educational books",    "Assets/Products/Books/books1.webp"));

        // ── Fashion (9 real images: fashion1–9) ──────────────────────────────
        p("Classic White Kurta",        "Premium cotton kurta for casual and festive occasions.",         899,  1299, 50, "Assets/Products/Fashion/fashion1.avif", 4.5, 128, fashion);
        p("Printed Floral Dress",       "Lightweight summer dress with vibrant floral print.",           1199, 1799, 35, "Assets/Products/Fashion/fashion2.avif", 4.3,  95, fashion);
        p("Slim Fit Denim Jacket",      "Versatile denim jacket great for layering.",                    1899, 2499, 28, "Assets/Products/Fashion/fashion3.avif", 4.7, 210, fashion);
        p("Ethnic Embroidered Top",     "Hand-embroidered cotton top with traditional patterns.",         1299, 1699, 40, "Assets/Products/Fashion/fashion4.avif", 4.4, 156, fashion);
        p("Relaxed Linen Shirt",        "Breathable linen shirt perfect for summer.",                     999, 1399, 55, "Assets/Products/Fashion/fashion5.avif", 4.2,  88, fashion);
        p("Boho Maxi Skirt",            "Flowy maxi skirt with bohemian print.",                         1099, 1499, 32, "Assets/Products/Fashion/fashion6.avif", 4.3, 112, fashion);
        p("Crop Jacket",                "Trendy cropped jacket for casual outings.",                     1499, 1999, 25, "Assets/Products/Fashion/fashion7.avif", 4.6, 178, fashion);
        p("Casual Polo Shirt",          "Comfortable polo shirt for everyday wear.",                      799, 1099, 60, "Assets/Products/Fashion/fashion8.webp", 4.1,  73, fashion);
        p("Formal Blazer",              "Sharp formal blazer for office and events.",                    2499, 3499, 18, "Assets/Products/Fashion/fashion9.avif", 4.8, 245, fashion);

        // ── Jewelry (jewelry1, Jewelry2–4, jewelry5–10) ─────────────────────
        p("Gold-plated Chain Necklace", "Elegant 18k gold-plated necklace for everyday elegance.",        599,  899, 60, "Assets/Products/Jewelry/jewelry1.avif",  4.6, 182, jewelry);
        p("Pearl Drop Earrings",        "Classic pearl drop earrings to complement any outfit.",           449,  699, 45, "Assets/Products/Jewelry/Jewelry2.avif",  4.4, 143, jewelry);
        p("Oxidised Silver Bracelet",   "Handcrafted oxidised silver bracelet with tribal motifs.",        749,  999, 38, "Assets/Products/Jewelry/Jewelry3.avif",  4.5, 107, jewelry);
        p("Diamond-cut Stud Earrings",  "Sparkling diamond-cut studs in rose gold finish.",                899, 1299, 30, "Assets/Products/Jewelry/Jewelry4.avif",  4.7, 234, jewelry);
        p("Kundan Choker Set",          "Traditional kundan choker with matching earrings.",              1899, 2699, 15, "Assets/Products/Jewelry/jewelry5.avif",  4.8, 312, jewelry);
        p("Twisted Gold Bangle",        "Sleek twisted gold bangle for a modern look.",                    649,  899, 42, "Assets/Products/Jewelry/jewelry6.avif",  4.3,  98, jewelry);
        p("Stone-studded Ring",         "Elegant stone-studded ring in silver finish.",                    399,  599, 55, "Assets/Products/Jewelry/jewelry7.avif",  4.5, 167, jewelry);
        p("Layered Chain Bracelet",     "Delicate layered chain bracelet for stacking.",                   499,  749, 48, "Assets/Products/Jewelry/jewelry8.avif",  4.4, 134, jewelry);
        p("Floral Pendant Necklace",    "Dainty floral pendant necklace in gold.",                         549,  799, 35, "Assets/Products/Jewelry/jewelry9.avif",  4.6, 189, jewelry);
        p("Antique Jhumka Earrings",    "Traditional antique jhumka earrings with ghungroo.",              699,  999, 28, "Assets/Products/Jewelry/jewelry10.avif", 4.9, 421, jewelry);

        // ── Watches (watches1–10) ─────────────────────────────────────────────
        p("Minimalist Leather Watch",   "Clean-dial leather strap watch for the modern professional.",   2499, 3499, 22, "Assets/Products/Watches/watches1.avif",  4.8, 320, watches);
        p("Sports Chronograph",         "Water-resistant sports watch with chronograph functionality.",  3299, 4999, 18, "Assets/Products/Watches/watches2.avif",  4.6, 265, watches);
        p("Rose Gold Mesh Watch",       "Elegant rose gold mesh band watch for women.",                  1899, 2799, 25, "Assets/Products/Watches/watches3.avif",  4.5, 198, watches);
        p("Classic Steel Watch",        "Timeless stainless steel watch with date display.",             2199, 2999, 30, "Assets/Products/Watches/watches4.avif",  4.7, 287, watches);
        p("Smart Digital Watch",        "Feature-rich digital smartwatch with health tracking.",         1799, 2499, 35, "Assets/Products/Watches/watches5.avif",  4.4, 156, watches);
        p("Vintage Skeleton Watch",     "Vintage skeleton dial watch with exposed movement.",            3999, 5499, 12, "Assets/Products/Watches/watches6.avif",  4.9, 178, watches);
        p("Pilot Chronograph Watch",    "Bold pilot-style chronograph with leather strap.",              4499, 5999,  8, "Assets/Products/Watches/watches7.avif",  4.8, 234, watches);
        p("Ladies Diamond-cut Watch",   "Elegant ladies watch with diamond-cut bezel.",                  2799, 3799, 20, "Assets/Products/Watches/watches8.avif",  4.7, 312, watches);
        p("Diver's Watch",              "Professional diver's watch, 200m water resistant.",             3599, 4999, 15, "Assets/Products/Watches/watches9.avif",  4.6, 198, watches);
        p("Luxury Gold Watch",          "Premium luxury watch with gold-plated case.",                   5999, 7999,  8, "Assets/Products/Watches/watches10.avif", 4.9, 445, watches);

        // ── Bags (bag1–10) ───────────────────────────────────────────────────
        p("Leather Tote Bag",           "Spacious genuine leather tote for office and weekend.",         2999, 3999, 30, "Assets/Products/bags/bag1.avif",  4.7, 198, bags);
        p("Mini Crossbody Bag",         "Compact crossbody bag with adjustable strap.",                  1299, 1799, 42, "Assets/Products/bags/bag2.avif",  4.5, 156, bags);
        p("Canvas Backpack",            "Durable canvas backpack for travel and college.",               1499, 1999, 38, "Assets/Products/bags/bag3.avif",  4.4, 134, bags);
        p("Quilted Shoulder Bag",       "Classic quilted shoulder bag in premium faux leather.",         1799, 2499, 25, "Assets/Products/bags/bag4.avif",  4.6, 212, bags);
        p("Woven Straw Bag",            "Chic woven straw bag for beach and casual outings.",             899, 1299, 45, "Assets/Products/bags/bag5.avif",  4.3,  87, bags);
        p("Structured Top-Handle Bag",  "Elegant structured bag with gold-tone hardware.",               2499, 3299, 20, "Assets/Products/bags/bag6.avif",  4.8, 267, bags);
        p("Drawstring Bucket Bag",      "Trendy drawstring bucket bag in soft leather.",                 1599, 2199, 33, "Assets/Products/bags/bag7.avif",  4.5, 143, bags);
        p("Laptop Briefcase",           "Professional briefcase with padded laptop compartment.",        2199, 2999, 22, "Assets/Products/bags/bag8.avif",  4.7, 189, bags);
        p("Belt Bag / Fanny Pack",      "Hands-free belt bag for travel and daily use.",                  799, 1099, 55, "Assets/Products/bags/bag9.avif",  4.2,  76, bags);
        p("Oversized Shopper Tote",     "Roomy shopper tote with zip closure.",                          1199, 1599, 40, "Assets/Products/bags/bag10.avif", 4.4, 112, bags);

        // ── Footwear (footwear1–9) ───────────────────────────────────────────
        p("Classic White Sneakers",     "Minimalist white sneakers that go with everything.",            1799, 2499, 45, "Assets/Products/Footwear/footwear1.avif", 4.6, 312, footwear);
        p("Leather Block Heels",        "Comfortable block-heel sandals for formal occasions.",          2199, 2999, 28, "Assets/Products/Footwear/footwear2.avif", 4.3, 167, footwear);
        p("Slip-on Loafers",            "Casual slip-on loafers in premium suede finish.",               1599, 2199, 35, "Assets/Products/Footwear/footwear3.avif", 4.5, 221, footwear);
        p("Running Sports Shoes",       "Lightweight cushioned running shoes for all terrain.",          2499, 3499, 40, "Assets/Products/Footwear/footwear4.avif", 4.7, 389, footwear);
        p("Strappy Heeled Sandals",     "Elegant strappy heeled sandals for parties.",                   1899, 2499, 22, "Assets/Products/Footwear/footwear5.avif", 4.4, 134, footwear);
        p("Chelsea Ankle Boots",        "Sleek chelsea boots in genuine leather.",                       3299, 4499, 18, "Assets/Products/Footwear/footwear6.avif", 4.8, 256, footwear);
        p("Flat Kolhapuri Chappals",    "Handcrafted kolhapuri chappals in leather.",                     699,  999, 60, "Assets/Products/Footwear/footwear7.avif", 4.5, 198, footwear);
        p("Platform Sneakers",          "Trendy platform sole sneakers for extra height.",               2199, 2999, 30, "Assets/Products/Footwear/footwear8.avif", 4.3, 112, footwear);
        p("Formal Oxford Shoes",        "Classic oxford shoes in polished leather for men.",             2799, 3799, 25, "Assets/Products/Footwear/footwear9.avif", 4.7, 178, footwear);

        // ── Beauty & Fragrance (fregrance1–6, fragrance7–10) ─────────────────
        p("Oud Rose Perfume",           "Rich oriental fragrance with oud and rose notes. 50ml EDP.",   2499, 3499, 20, "Assets/Products/Beauty & Fragrance/fregrance1.avif",  4.8, 445, beauty);
        p("Fresh Citrus Cologne",       "Light and refreshing citrus cologne for daily wear. 75ml.",    1499, 1999, 35, "Assets/Products/Beauty & Fragrance/fregrance2.avif",  4.5, 287, beauty);
        p("Midnight Musk EDP",          "Deep, sensual musk fragrance for evening wear. 100ml.",        3299, 4499, 15, "Assets/Products/Beauty & Fragrance/fregrance3.avif",  4.7, 312, beauty);
        p("Floral Bloom EDT",           "Light and feminine floral eau de toilette. 60ml.",             1299, 1799, 28, "Assets/Products/Beauty & Fragrance/fregrance4.webp",  4.4, 198, beauty);
        p("Sandalwood Attar",           "Pure sandalwood attar — long-lasting natural fragrance.",      1899, 2499, 22, "Assets/Products/Beauty & Fragrance/fregrance5.avif",  4.9, 523, beauty);
        p("Aqua Marine Body Mist",      "Refreshing aqua marine body mist. 200ml.",                      699,  999, 50, "Assets/Products/Beauty & Fragrance/fregrance6.avif",  4.2, 134, beauty);
        p("Amber Wood Perfume",         "Warm amber and woody fragrance for all seasons.",               2199, 2999, 18, "Assets/Products/Beauty & Fragrance/fragrance7.avif",  4.6, 267, beauty);
        p("Rose Garden EDP",            "Romantic rose-forward EDP for special occasions.",              1999, 2799, 25, "Assets/Products/Beauty & Fragrance/fragrance8.avif",  4.5, 189, beauty);
        p("Vanilla Dreams Perfume",     "Sweet vanilla and tonka bean fragrance. 50ml.",                1799, 2299, 30, "Assets/Products/Beauty & Fragrance/fragrance9.avif",  4.7, 312, beauty);
        p("Black Oud Intense",          "Intense black oud with smoky amber. 100ml EDP.",               3999, 5499, 10, "Assets/Products/Beauty & Fragrance/fragrance10.avif", 4.9, 678, beauty);

        // ── Audio & Electronics (audio1–9) ───────────────────────────────────
        p("Wireless Noise-Cancelling Headphones", "Premium ANC headphones, 30-hour battery life.",     4999, 6999, 15, "Assets/Products/Audio & Electronics/audio1.webp", 4.9, 412, audio);
        p("Bluetooth Earbuds TWS",      "True wireless earbuds with active noise cancellation.",        2499, 3499, 25, "Assets/Products/Audio & Electronics/audio2.webp", 4.6, 289, audio);
        p("Portable Bluetooth Speaker", "360° surround sound, 24-hour battery, waterproof.",           1999, 2799, 32, "Assets/Products/Audio & Electronics/audio3.webp", 4.7, 356, audio);
        p("Over-Ear Studio Headphones", "Professional studio-quality sound for audiophiles.",           3499, 4999, 12, "Assets/Products/Audio & Electronics/audio4.webp", 4.8, 178, audio);
        p("Neckband Bluetooth Earphones","Comfortable neckband with 20-hour playback.",                1299, 1799, 40, "Assets/Products/Audio & Electronics/audio5.webp", 4.4, 234, audio);
        p("Party Speaker with LED",     "Colourful LED party speaker with deep bass.",                  2799, 3799, 20, "Assets/Products/Audio & Electronics/audio6.webp", 4.5, 198, audio);
        p("Wired In-Ear Earphones",     "High-fidelity wired earphones with mic. Type-C.",              499,  799, 80, "Assets/Products/Audio & Electronics/audio7.webp", 4.2,  87, audio);
        p("Smart Home Speaker",         "Voice-controlled smart speaker with assistant.",              2999, 3999, 18, "Assets/Products/Audio & Electronics/audio8.webp", 4.6, 312, audio);
        p("Soundbar for TV",            "2.1 channel soundbar with wireless subwoofer.",               4499, 5999, 10, "Assets/Products/Audio & Electronics/audio9.webp", 4.8, 267, audio);

        // ── Gadgets (gadgets1–6) ─────────────────────────────────────────────
        p("Wireless Charging Pad",      "Fast 15W Qi wireless charger compatible with all devices.",    999, 1499, 40, "Assets/Products/Gadgets/gadgets4.webp", 4.5, 267, gadgets);
        p("Smart Fitness Band",         "24/7 heart rate, SpO2, sleep tracking. 7-day battery.",       1999, 2999, 28, "Assets/Products/Gadgets/gadgets5.webp", 4.4, 189, gadgets);
        p("Portable Power Bank 20000mAh","Fast charge 20000mAh power bank with dual USB-C.",           1499, 1999, 50, "Assets/Products/Gadgets/gadgets6.webp", 4.6, 334, gadgets);
        p("Smart Watch",                "Multifunctional smartwatch with GPS and health tracking.",     4999, 6499, 15, "Assets/Products/Gadgets/gadgets1.avif", 4.7, 412, gadgets);
        p("Mini Drone with Camera",     "Compact folding drone with 1080p HD camera.",                 5999, 7999, 10, "Assets/Products/Gadgets/gadgets2.webp", 4.5, 156, gadgets);
        p("USB-C Hub 7-in-1",           "Multiport USB-C hub with HDMI, SD card, USB 3.0.",             899, 1299, 45, "Assets/Products/Gadgets/gadgets3.webp", 4.3, 223, gadgets);

        // ── Books (books1, book2–7) ───────────────────────────────────────────
        p("Atomic Habits",              "James Clear — Build good habits and break bad ones.",           399,  499, 100, "Assets/Products/Books/books1.webp", 4.9, 1250, books);
        p("The Alchemist",              "Paulo Coelho — A magical story about following your dreams.",   299,  399,  80, "Assets/Products/Books/book2.webp",  4.8,  980, books);
        p("Deep Work",                  "Cal Newport — Rules for focused success in a distracted world.",349,  449,  90, "Assets/Products/Books/book3.webp",  4.7,  765, books);
        p("The Psychology of Money",    "Morgan Housel — Timeless lessons on wealth and happiness.",    379,  499,  75, "Assets/Products/Books/book4.webp",  4.8,  892, books);
        p("Rich Dad Poor Dad",          "Robert Kiyosaki — What the rich teach about money.",           299,  399,  85, "Assets/Products/Books/book5.webp",  4.6, 1100, books);
        p("Think and Grow Rich",        "Napoleon Hill — The classic guide to success mindset.",        349,  449,  70, "Assets/Products/Books/book6.webp",  4.7,  934, books);
        p("The 48 Laws of Power",       "Robert Greene — Laws that govern power dynamics.",             449,  599,  65, "Assets/Products/Books/book7.webp",  4.5,  678, books);

        // ── Admin user ────────────────────────────────────────────────────────
        if (!userRepository.existsByEmail("admin@9tails.com")) {
            userRepository.save(User.builder()
                    .fullName("Admin")
                    .email("admin@9tails.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role(User.Role.ADMIN)
                    .build());
        }

        log.info("Database seeded: {} categories, {} products.",
                categoryRepository.count(), productRepository.count());
    }

    private Category save(Category c) { return categoryRepository.save(c); }

    private Category cat(String name, String desc, String img) {
        return Category.builder().name(name).description(desc).imageUrl(img).build();
    }

    private void p(String name, String desc, long price, long orig, int stock,
                   String img, double rating, int reviews, Category cat) {
        productRepository.save(Product.builder()
                .name(name)
                .description(desc)
                .price(BigDecimal.valueOf(price))
                .originalPrice(BigDecimal.valueOf(orig))
                .stock(stock)
                .imageUrl(img)
                .galleryImages(null)   // single image only — no gallery
                .rating(rating)
                .reviewCount(reviews)
                .category(cat)
                .active(true)
                .build());
    }
}

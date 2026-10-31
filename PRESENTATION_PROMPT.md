# 9Tails Ecommerce Project Presentation Prompt

Create exactly nine slides for a four-minute project presentation. Use a clean, professional 16:9 design with dark navy, white, and blue accents. Keep the slide order fixed. Use only facts verified in the 9Tails Ecommerce source code and README. Do not claim that unimplemented future items already exist.

1. **Project Intro** — 9Tails is a full-stack ecommerce marketplace with customer shopping plus seller, warehouse, delivery-agent, and administrator workflows.
2. **Tech Stack** — Vanilla HTML/CSS/JavaScript; Java 21 and Spring Boot 3.2.5; Spring Security with JWT and BCrypt; Spring Data JPA/Hibernate; MySQL 8; Maven; Razorpay Test Mode; Nominatim address search and Google Maps location previews/routes.
3. **Project Structure** — Show `Home/`, `Assets/`, `backend/`, `database/`, `start.ps1`, and `README.md`, explaining that the frontend is static browser code and the backend follows controller-service-repository-entity layering.
4. **ER Diagram** — Show users, addresses, categories, products, cart items, wishlist items, orders, order items, seller fulfillments, warehouse locations, delivery events, stage tokens/OTP, business locations, and service requests. Mark the relations clearly.
5. **Page Screenshots** — Cover Home, Product Detail, Cart, Orders, Order Tracking, Operations Studio, Delivery Workspace, Admin Dashboard, and the registration pages in user-flow order. State that role pages require JWT sign-in.
6. **New Features Added** — Seller fulfillment workflow, two-hub delivery routing, QR and OTP delivery checks, Razorpay signature verification, customer rating, return/exchange service requests with evidence, and notifications.
7. **Important Algorithms** — Haversine nearest-warehouse selection; fulfillment state machine; one-minute agent offers and 24-hour route deadlines; server-calculated delivery charge and backend Razorpay signature verification.
8. **Major Challenge Faced** — Keeping multiple seller, warehouse, and agent transitions consistent. Explain the solution: seller-level fulfillment records, server validation, audit events, notifications, token revocation, offer expiry, and parent order status recalculation.
9. **What You'll Add Next** — Production hosting and managed secrets/storage, real-time updates, automated refunds after review, and more integration tests and observability.

Audience questions will focus on the new features, challenge, and algorithms. Use short, direct language and do not overcrowd a slide.

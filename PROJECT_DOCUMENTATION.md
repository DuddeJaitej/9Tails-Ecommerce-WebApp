# 9Tails Ecommerce: End-to-End Project Documentation

## 1. Project purpose

9Tails is a full-stack ecommerce application. Customers can browse a catalog, manage a cart and wishlist, save addresses, place cash-on-delivery or Razorpay orders, follow fulfillment progress, rate delivered items, and request returns or exchanges. The project also includes operational workspaces for sellers, warehouse operators, delivery agents, and administrators.

The distinguishing part of the application is its fulfillment model. An order can be broken into seller-specific fulfillment records, routed through approved warehouses, assigned to distinct delivery agents for pickup, transfer, and final delivery, then confirmed by the customer.

## 2. Architecture

```text
Browser
  ├─ Home/ static HTML, CSS and JavaScript pages
  ├─ api.js adds JWT authorization and calls the API
  └─ Assets/ supplies local product and hero media
            │ HTTP + JSON
            ▼
Spring Boot API, port 8081, context path /api
  ├─ Controllers expose REST endpoints
  ├─ Services enforce business rules
  ├─ Spring Security validates JWTs and roles
  └─ Spring Data JPA/Hibernate persists entities
            │
            ▼
MySQL 8 database: nintails_ecommerce
```

The front end is framework-free. It uses page-specific scripts in `Home/`, a centralized API wrapper in `Home/api.js`, and browser `localStorage` for session helpers and fallback client state. The main backend package is `com.nintails.ecommerce` under `backend/src/main/java`.

## 3. Technology stack

| Layer | Technology | Responsibility |
|---|---|---|
| Front end | HTML5, CSS3, vanilla JavaScript | Pages, responsive UI, interaction handling |
| Back end | Java 21, Spring Boot 3.2.5 | REST API and business services |
| Persistence | Spring Data JPA and Hibernate | Entity mapping, repositories, database access |
| Database | MySQL 8 | Users, catalog, orders, fulfillment, audit data |
| Security | Spring Security, JWT, BCrypt | Stateless authentication and role checks |
| Payments | Razorpay Test Mode | Payment-order creation and signature validation |
| Location support | Nominatim and Google Maps | Address lookup, embedded location previews, and external route navigation |
| Build/run | Maven, PowerShell, Python static server | Backend build and local development |

## 4. Main roles

| Role | What it does |
|---|---|
| Customer (`USER`) | Shops, manages cart/wishlist/addresses, pays, tracks orders, confirms delivery, rates items, and raises service requests. |
| Seller (`OWNER`) | Manages assigned products and business pickup locations, then confirms or rejects seller fulfillment tasks. |
| Warehouse operator (`WAREHOUSE_OPERATOR`) | Registers a hub, handles package receipt, selects transfer and final-delivery agents, and manages warehouse work. |
| Delivery agent (`DELIVERY_AGENT`) | Accepts timed offers, scans package/stage tokens, records pickup or warehouse movement, and performs final delivery. |
| Administrator (`ADMIN`) | Manages the dashboard, catalog, categories, staff, warehouses, business locations, customers, inventory, and delivery events. |

## 5. Front-end pages

| Page | Main responsibility |
|---|---|
| `Home.html` | Storefront, search, filtering, catalog, wishlist and sign-in entry point. |
| `ProductDetail.html` | Product gallery, related products and cart action. |
| `Cart.html` | Quantity changes, address selection, COD checkout, Razorpay checkout. |
| `Orders.html` | Customer order history and post-order actions. |
| `OrderTracking.html` | Fulfillment timeline, delivery confirmation, ratings, return/exchange requests. |
| `Operations.html` | Owner workspace for products, pickup locations, fulfillment and warehouse-related operational views. |
| `Delivery.html` | Delivery-agent and warehouse task workspace, QR scanning and handoffs. |
| `Admin.html` | Administrator login and analytics/management dashboard. |
| Registration pages | Separate registration routes for admin, owner, warehouse operator and delivery agent. |

Shared browser modules include `api.js` for API calls, `auth.js` for session/JWT handling, `maps.js` for external map URLs, and `notifications.js` for notification UI.

## 6. Request and code flow

### 6.1 General API request

1. A page script calls a method in `Home/api.js`.
2. The client adds `Authorization: Bearer <JWT>` when the endpoint needs authentication.
3. A Spring controller accepts and validates the request DTO.
4. A service performs authorization and business-rule checks.
5. The service loads and saves entities through Spring Data repositories.
6. The controller returns JSON DTOs to the browser.
7. The page script renders the response or shows an error.

### 6.2 Authentication flow

1. A user registers or signs in through `/api/auth` endpoints.
2. `AuthService` validates credentials and uses BCrypt password hashes.
3. `JwtUtils` creates a signed access token.
4. The browser stores the token under a role-appropriate key in `localStorage`.
5. `JwtAuthFilter` validates the token on later requests.
6. Spring Security and method-level authorization allow only the required role to execute protected actions.

### 6.3 Catalog, cart, and order flow

1. The storefront requests paginated products and categories.
2. Customers search/filter product results and add an item to the cart.
3. `CartService` stores the cart item against the signed-in user and product.
4. At checkout, `OrderService` loads the server-side cart and selected address.
5. It calculates subtotal and delivery charge, creates order items, reserves stock, and creates seller fulfillment records.
6. COD can place an order directly. For online payments, the backend creates the Razorpay order and later verifies the returned payment signature before setting payment status to `PAID`.
7. The cart is cleared only after the order succeeds.

### 6.4 Fulfillment and delivery flow

```text
Order placed
  → Seller confirms fulfillment
  → System selects nearest approved origin and destination warehouses
  → Origin operator assigns pickup agent
  → Agent accepts timed offer and confirms pickup
  → Origin warehouse receives package
  → Transfer agent moves package when two hubs are required
  → Destination warehouse receives package
  → Destination operator assigns a distinct final-delivery agent
  → Agent dispatches and requests customer confirmation
  → Customer confirms delivery
```

The flow skips the transfer leg when the origin and destination warehouse are the same. The backend records delivery events, creates notifications, revokes stale stage tokens, and derives the parent order's customer-facing status from its active seller fulfillments.

## 7. Key business logic

### Server-calculated totals

`OrderService` calculates prices from persisted cart entries rather than trusting totals sent by the browser. The delivery rule is:

```text
Subtotal > ₹500  → delivery charge ₹0
Subtotal ≤ ₹500  → delivery charge ₹10
Empty active order → delivery charge ₹0
```

### Payment verification

`RazorpayPaymentGateway` creates payment orders with server-calculated paise values. It checks the Razorpay signature before the order becomes paid. Closing or failing checkout does not mark the order as paid.

### Warehouse selection

`DeliveryWorkflowService` contains a Haversine-distance calculation. It compares stored latitude/longitude pairs and chooses the nearest approved warehouse to the seller as origin and the nearest approved warehouse to the customer as destination.

### Delivery-state protection

Delivery transitions are enforced by the backend service, not by UI labels. The code checks actor roles, assigned warehouse, accepted delivery offer, token validity, deadlines, and the current fulfillment status before changing state.

### Time-bounded assignments

Agent offers expire after one minute. Accepted pickup, transfer, and final-delivery legs have a 24-hour route deadline. When an offer or deadline expires, the service clears the assignment, records an event, and notifies the responsible operator or administrator.

### QR and OTP security

Delivery stage tokens store a hash for comparison and encrypted/raw-token material for controlled use. Tokens have expiry, used, and revoked timestamps. Delivery OTP challenges also store hashes, expiry, failed attempts, and consumption time.

## 8. Database structure

### Core commerce tables

| Table | Important relationships |
|---|---|
| `users` | Parent for addresses, orders, cart items, wishlist items and operational assignments. |
| `categories` | Has many products. |
| `products` | Belongs to a category; may reference an owner and business location. |
| `addresses` | Belongs to a user. |
| `cart_items` | Joins a user and product. |
| `wishlist_items` | Joins a user and product. |
| `orders` | Belongs to a user; contains order items and overall payment/status fields. |
| `order_items` | Belong to an order and product; may reference a fulfillment. |

### Fulfillment and operations tables

| Table | Purpose |
|---|---|
| `business_locations` | Seller pickup locations owned by users. |
| `warehouse_locations` | Warehouse addresses, coordinates, approval/active state, and operator. |
| `seller_fulfillments` | Seller-specific work for an order, warehouses, agents, dates and customer response. |
| `delivery_stage_tokens` | Expiring, revocable QR/stage tokens for a fulfillment. |
| `delivery_events` | Audit trail of actor, previous/new state, outcome, package condition and notes. |
| `delivery_otp_challenges` | Time-limited delivery confirmation OTPs. |
| `notifications` | In-app notifications tied to recipient, order and optional fulfillment. |
| `service_requests` | Return/exchange requests, evidence URLs, review status, warehouse and agent steps. |
| `phone_verification_challenges` | Phone verification code lifecycle. |
| `admin_password_reset_tokens` | Administrator password-reset lifecycle. |

### Key ER relationships

```text
USERS 1 ── * ADDRESSES
USERS 1 ── * ORDERS 1 ── * ORDER_ITEMS * ── 1 PRODUCTS * ── 1 CATEGORIES
USERS 1 ── * CART_ITEMS * ── 1 PRODUCTS
USERS 1 ── * WISHLIST_ITEMS * ── 1 PRODUCTS
ORDERS 1 ── * SELLER_FULFILLMENTS 1 ── * DELIVERY_EVENTS
SELLER_FULFILLMENTS 1 ── * DELIVERY_STAGE_TOKENS
ORDER_ITEMS 1 ── * SERVICE_REQUESTS
USERS 1 ── * BUSINESS_LOCATIONS
```

The SQL base schema lives in `backend/database/schema.sql`. `backend/database/delivery_workflow_migration.sql` adds the delivery workflow tables, extends order status/payment enums, and applies relational constraints for the operational model.

## 9. API surface by domain

| Domain | Main endpoint prefix | Examples |
|---|---|---|
| Authentication | `/api/auth` | register, role-specific login, Google login, password reset, delivery/owner registration |
| Products and categories | `/api/products`, `/api/categories` | catalog, search, filter, related products, reviews, management |
| Cart and wishlist | `/api/cart`, `/api/wishlist` | add/update/remove cart items, save products |
| Orders | `/api/orders` | place order, create/verify Razorpay order, details, ratings, service requests |
| Customer profile | `/api/addresses`, `/api/users` | saved addresses and profile data |
| Owner operations | `/api/owner/fulfillments`, `/api/business-locations` | seller decisions, locations and stage QR retrieval |
| Delivery operations | `/api/delivery`, `/api/warehouse/delivery-agents` | tasks, offers, scans, warehouse receipt, handoff and confirmation |
| Administration | `/api/admin`, `/api/admin/warehouses`, `/api/admin/business-locations` | dashboard, inventory, customer, warehouse and location management |
| Notifications | `/api/notifications` | list and manage delivery/assignment notices |

## 10. Security and configuration

The API runs on port `8081` with context path `/api`. It uses MySQL database `nintails_ecommerce`. Environment variables or a local ignored `.env` file provide database, JWT, Razorpay, SMTP and upload configuration.

Important runtime settings include `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and optional upload locations. Secrets should never be committed. Product images and order evidence use configurable filesystem storage; production deployment should point these to persistent managed storage.

## 11. Current limitations and next steps

The code already implements the core workflows, but production readiness should add managed secret storage, persistent uploads, HTTPS, monitoring, automated refund handling after review, and broader integration testing around payment failures and concurrent fulfillment transitions. Real-time push/WebSocket updates would also remove the need for users to refresh task or tracking views.

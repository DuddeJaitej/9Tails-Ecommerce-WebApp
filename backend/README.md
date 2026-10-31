# 9Tails Ecommerce — Spring Boot Backend

Production-level REST API built with Spring Boot 3, Hibernate JPA, MySQL, and JWT authentication.

---

## Tech Stack

| Layer       | Technology                         |
|-------------|-----------------------------------|
| Framework   | Spring Boot 3.2.5                 |
| Security    | Spring Security + JWT (jjwt 0.11) |
| ORM         | Hibernate / Spring Data JPA       |
| Database    | MySQL 8.x                         |
| Build       | Maven 3.9 + Java 21               |
| Utilities   | Lombok, Bean Validation           |

---

## Prerequisites

- Java 21 JDK
- Maven 3.9+
- MySQL 8.x running on localhost:3306
- MySQL Workbench (optional but recommended)

---

## Step-by-Step Setup

### Step 1 — Configure credentials and run SQL

Set the backend credentials in the shell before starting the application:

```powershell
$env:DB_PASSWORD = "your-local-mysql-password"
$env:JWT_SECRET = "a-long-random-secret-at-least-32-characters"
```

`JWT_SECRET` must contain at least 32 UTF-8 bytes. The backend validates the signing key during startup.

Never commit real database passwords or production JWT secrets.

For Razorpay Test Mode, run `.\configure-razorpay.ps1` from this backend folder and enter your rotated Key ID and Key Secret when prompted. The secret prompt is hidden. This writes the ignored `.env` file; the secret remains server-side and must never be added to frontend code or committed. Restart the backend after configuring or rotating keys.

1. Open **MySQL Workbench** and connect to `localhost` using:
   - Username: `root`
   - Password: the value configured in `DB_PASSWORD`
2. Open `database/schema.sql` and run it to create the database and seed the catalog.
3. Back up existing installations before starting the updated backend. On startup, it
   automatically runs `database/delivery_workflow_migration.sql` before seeding accounts;
   this adds the owner, delivery, warehouse, notification, verification, and stock-reservation
   schema and upgrades existing fulfillment and order status columns for every delivery stage.
   MySQL 8.0 or later is required, and the database account needs schema-alter privileges.

Administrator credentials are provisioned from `ADMIN_EMAIL` and
`ADMIN_PASSWORD`; administrator signup is not public. Businesses can self-register as OWNER
through the Operations Studio sign-in page; passwords must be at least 12 characters. The same
sign-in form accepts operations and administrator accounts without an account-type selector.
Admin bootstrap requires a valid email and a password of at least 12 characters. Missing or invalid
bootstrap settings are logged as a warning and do not prevent the API from starting; no admin
account is created or reset until valid settings are configured.

Warehouse operators self-register at `Home/DeliveryRegister.html`, providing the hub address
and map coordinates. A new hub stays inactive until an administrator approves it in
`Home/Admin.html`. Delivery agents register by selecting an approved hub and stay inactive
until its operator approves them in `Home/Operations.html`; administrator invitation codes are
not part of registration. After an owner confirms an order, the workflow chooses the nearest
approved, staffed hub to the seller and the nearest approved hub to the customer. The origin
warehouse reviews the seller pickup address and route, then accepts or declines the pickup
request. A decline is recorded and routes the request to the next-nearest eligible hub when
available; if none is available, administrators are notified. After acceptance, the warehouse
assigns an active agent registered at that hub. Owner and warehouse decisions are retained in
the delivery event history and shown in the administrator dashboard. If origin and destination
are the same hub, the transfer leg is skipped, but that warehouse operator still chooses a
distinct final-delivery agent after check-in. When the hubs differ, the origin operator assigns
a distinct transfer agent after pickup check-in; that agent scans the package QR to dispatch the
parcel and scans the destination hub's arrival QR to finish the handoff. The destination operator
then assigns another agent for final delivery. Offers expire after one minute if not accepted,
and accepted assignments have a 24-hour deadline.
The web app stores coordinates from address search, previews selected locations with Google Maps
embeds, and opens Google Maps externally to navigate routes. The map embeds do not require a
Google Maps API key.
Address lookup uses OpenStreetMap's Nominatim search service.

Owners must add and confirm a pickup location before an admin can assign existing products to
them. Owners can create products and locations in that same operations page. Uploaded product images are served
publicly from `/api/uploads/products/**` so storefront image elements can load them without
an authorization header. Files are written to `uploads/products` relative to the backend
working directory by default. Configure `APP_UPLOAD_DIRECTORY` to a persistent directory
and `UPLOAD_PUBLIC_BASE_URL` to the externally reachable backend API base URL in production.

Delivery QR camera scanning requires HTTPS or localhost; the page also supports manual token
entry. The owner creates one package QR at order confirmation. The pickup agent scans it at the
seller, then scans the origin warehouse's separate arrival QR. For a two-hub route, the origin
operator assigns a transfer agent, who scans the package QR to dispatch the parcel and the
destination warehouse's arrival QR to confirm receipt. For either route type, the destination
operator assigns the final-delivery agent, who scans the package QR to start the customer leg.
The server validates the assigned agent, warehouse, and fulfillment status for each handoff.
Warehouse operators display their persistent arrival QR in the delivery workspace; customer
tracking advances from the validated scans. After the final handoff, the authenticated customer
confirms receipt from their order page.
Expected delivery is five calendar days from seller confirmation in the Asia/Kolkata timezone.
Paid-order cancellations or seller rejections are marked for administrator refund review; no
automated Razorpay refund is performed by this workflow.

The hard-coded demo catalog reconciler is disabled by default because it overwrites matching
product prices, stock, and ratings on every startup. For a disposable local demo only, set
`SEED_DEMO_DATA=true`; never enable this against production data.

### Step 2 — Start the Spring Boot Application

Open a terminal in the `backend` folder and run:

```bash
mvn spring-boot:run
```

Or build a JAR and run it:

```bash
mvn clean package -DskipTests
java -jar target/ecommerce-1.0.0.jar
```

The server starts at: **http://localhost:8081/api**

---

## API Reference

All endpoints are prefixed with `/api`

### Authentication (Public)

| Method | Endpoint         | Body                                  | Description     |
|--------|------------------|---------------------------------------|-----------------|
| POST   | /auth/register   | `{fullName, email, password}`         | Register user   |
| POST   | /auth/login      | `{email, password}`                   | Login, get JWT  |

**Login Response:**
```json
{
  "success": true,
  "data": {
    "token": "eyJ...",
    "type": "Bearer",
    "userId": 1,
    "email": "user@example.com",
    "fullName": "John Doe",
    "role": "USER"
  }
}
```

Use the token in all subsequent requests:
```
Authorization: Bearer eyJ...
```

---

### Products (GET = Public, POST/PUT/DELETE = Admin only)

| Method | Endpoint                        | Description                          |
|--------|---------------------------------|--------------------------------------|
| GET    | /products                       | All products (page, size, sort)      |
| GET    | /products/{id}                  | Single product                       |
| GET    | /products/category/{name}       | By category name                     |
| GET    | /products/search?q=watch        | Full-text search                     |
| GET    | /products/filter?min=100&max=5000 | Price range filter                 |
| GET    | /products/{id}/related          | Related products                     |
| POST   | /products                       | Create product (ADMIN)               |
| PUT    | /products/{id}                  | Update product (ADMIN)               |
| DELETE | /products/{id}                  | Soft-delete product (ADMIN)          |

**Query params for listing:** `?page=0&size=20&sort=newest|price-low|price-high|rating`

---

### Categories (GET = Public)

| Method | Endpoint           | Description           |
|--------|--------------------|-----------------------|
| GET    | /categories        | All categories        |
| GET    | /categories/{id}   | Single category       |
| POST   | /categories        | Create (ADMIN)        |
| PUT    | /categories/{id}   | Update (ADMIN)        |
| DELETE | /categories/{id}   | Delete (ADMIN)        |

---

### Cart (Authenticated)

| Method | Endpoint              | Params / Body                | Description              |
|--------|-----------------------|------------------------------|--------------------------|
| GET    | /cart                 | —                            | Get cart with totals     |
| POST   | /cart                 | `?productId=1&quantity=2`    | Add item                 |
| PUT    | /cart/{productId}     | `?quantity=3`                | Update quantity          |
| DELETE | /cart/{productId}     | —                            | Remove item              |
| DELETE | /cart                 | —                            | Clear entire cart        |

---

### Orders (Authenticated)

| Method | Endpoint                  | Body / Params               | Description               |
|--------|---------------------------|------------------------------|---------------------------|
| POST   | /orders                   | `{addressId, paymentMethod}` | Place order from cart     |
| GET    | /orders                   | `?page=0&size=10`            | My orders (newest first)  |
| GET    | /orders/{id}              | —                            | Order detail              |
| PATCH  | /orders/{id}/cancel       | —                            | Cancel order              |
| PATCH  | /orders/{id}/return       | —                            | Request return            |
| PATCH  | /orders/{id}/exchange     | —                            | Request exchange          |
| PATCH  | /orders/{id}/status       | `?status=SHIPPED` (ADMIN)   | Update order status       |

**paymentMethod values:** `COD` or `UPI`

---

### Addresses (Authenticated)

| Method | Endpoint           | Body                                              | Description     |
|--------|--------------------|---------------------------------------------------|-----------------|
| GET    | /addresses         | —                                                 | My addresses    |
| POST   | /addresses         | `{fullName,street,city,postalCode,country}`       | Add address     |
| PUT    | /addresses/{id}    | same as above                                     | Update address  |
| DELETE | /addresses/{id}    | —                                                 | Delete address  |

---

### Wishlist (Authenticated)

| Method | Endpoint                  | Description                         |
|--------|---------------------------|-------------------------------------|
| GET    | /wishlist                 | My wishlist (full product details)  |
| POST   | /wishlist/{productId}     | Toggle (add / remove)               |
| GET    | /wishlist/{productId}/status | Check if product is wishlisted   |

---

### Users (Authenticated)

| Method | Endpoint   | Description                   |
|--------|------------|-------------------------------|
| GET    | /users/me  | Current user profile          |
| GET    | /users     | All users (ADMIN only)        |

---

## Admin Dashboard Sign-in

Admin sign-in is separate from customer registration and login. Public administrator signup is
limited to initial setup: the Admin sign-in page offers first-admin registration only when the
database has no administrator account. Registration closes as soon as the first administrator is
created. Alternatively, configure `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `backend/.env` to
provision the initial administrator account on startup. Changing the environment password later
does not overwrite a password set by an administrator. Signed-in administrators can create
additional admin accounts from `Home/AdminRegister.html`; that page uses the protected
admin-registration endpoint.
If an existing admin password is unknown and SMTP reset is unavailable, you can explicitly reset
only the account named by `ADMIN_EMAIL`: set a new `ADMIN_PASSWORD` and
`ADMIN_PASSWORD_RESET_ON_STARTUP=true` in the ignored `backend/.env`, restart the backend once,
then set the flag back to `false` and restart again. Keep the reset flag disabled during normal
operation and never commit `.env`.
On Windows, run `.\reset-admin-password.ps1` from this folder to be prompted for the admin email
and password securely; the script validates the password and enables the reset flag only for the
backend process it starts.
Customer registration and Google sign-in can only create or authenticate `USER` accounts. The
dashboard stores its browser token separately from the storefront session.

### Admin and customer password reset email

The Admin sign-in page and storefront login modal send one-time reset links by email. Configure SMTP in the ignored
`backend/.env` file using `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and
`SMTP_FROM`; `SMTP_PASSWORD` should be an app password when your provider requires one. The link
expires after 30 minutes and can be used once. `ADMIN_PASSWORD_RESET_URL` and
`CUSTOMER_PASSWORD_RESET_URL` can override the respective frontend reset page URLs. Do not put
SMTP credentials in source control or chat.

---

## Database Tables

| Table           | Purpose                              |
|-----------------|--------------------------------------|
| users           | Registered users with roles          |
| categories      | Product categories (Fashion, etc.)   |
| products        | All product listings                 |
| addresses       | User delivery addresses              |
| orders          | Placed orders with status tracking   |
| order_items     | Line items per order                 |
| cart_items      | Active cart items per user           |
| wishlist_items  | Saved wishlist items per user        |

---

## Response Format

All responses follow this structure:

```json
{
  "success": true,
  "message": "Optional message",
  "data": { ... },
  "timestamp": "2026-09-07T10:00:00"
}
```

Error responses:
```json
{
  "success": false,
  "message": "Resource not found with id: 99",
  "timestamp": "2026-09-07T10:00:00"
}
```

---

## Pagination Response

Paginated endpoints return:

```json
{
  "success": true,
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 80,
    "totalPages": 4,
    "last": false
  }
}
```

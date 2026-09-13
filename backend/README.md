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

### Step 1 — Run SQL in MySQL Workbench

1. Open **MySQL Workbench** and connect to `localhost` using:
   - Username: `root`
   - Password: `Jai1421tej@333`
2. Open the file: `database/schema.sql`
3. Click **Run (⚡)** — this will:
   - Create the `nintails_ecommerce` database
   - Create all 8 tables
   - Insert 9 categories, 12 sample products, 1 admin user

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

The server starts at: **http://localhost:8080/api**

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

## Default Admin Credentials

```
Email:    admin@9tails.com
Password: Admin@123
```

Use these to login and get an ADMIN JWT token for creating products/categories.

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

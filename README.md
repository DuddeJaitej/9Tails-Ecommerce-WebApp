# 9Tails Ecommerce

A full-stack ecommerce web application built with vanilla HTML/CSS/JS frontend and Spring Boot REST API backend.

## Tech Stack

**Frontend**
- Vanilla HTML5, CSS3, JavaScript (no frameworks)
- Responsive design with CSS custom properties
- JWT authentication, localStorage + API hybrid data layer

**Backend**
- Spring Boot 3.2.5 + Java 21
- Spring Security + JWT (stateless)
- Hibernate / Spring Data JPA
- MySQL 8 database
- Maven 3.9

## Features

- Product catalog with categories, search, filters and ratings
- Product detail page with single image view
- Shopping cart with quantity controls
- Order placement (COD + UPI via Razorpay)
- Order tracking with 4-step shipping timeline
- Cancel / Return / Exchange order actions
- Wishlist management
- User authentication (register / login / Google OAuth)
- Admin product & category management

## Project Structure

```
Ecommerce/
├── Home/           ← Frontend (HTML, CSS, JS)
│   ├── Home.html
│   ├── Cart.html
│   ├── Orders.html
│   ├── OrderTracking.html
│   ├── ProductDetail.html
│   ├── api.js          ← centralized API client
│   ├── auth.js         ← JWT auth layer
│   ├── Home.js
│   ├── ProductDetail.js
│   └── OrderTracking.js
├── Assets/         ← Product images
└── backend/        ← Spring Boot API
    ├── src/
    ├── pom.xml
    └── database/schema.sql
```

## Running Locally

### 1. Database
Run `backend/database/schema.sql` in MySQL Workbench
*(or just start Spring Boot — it auto-creates and seeds the DB)*

### 2. Backend
```bash
cd backend
mvn spring-boot:run
```
API runs at: `http://localhost:8081/api`

### 3. Frontend
Open `Home/Home.html` with VS Code Live Server.

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /auth/register | Public | Register user |
| POST | /auth/login | Public | Login, get JWT |
| GET | /products | Public | List products (paginated) |
| GET | /products/{id} | Public | Single product |
| GET | /cart | Required | Get cart |
| POST | /cart | Required | Add to cart |
| POST | /orders | Required | Place order |
| GET | /orders | Required | My orders |
| GET | /wishlist | Required | My wishlist |

## Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@9tails.com | Admin@123 |

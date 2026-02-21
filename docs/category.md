# Category API Specification

## Base URL
```
/api/v1/categories
```

## Authentication
- Endpoints require `Authorization: Bearer {token}` header (JWT issued by `/api/v1/users/login`).

## Resource
- Category fields:
	- `id`: string
	- `name`: string (required, unique per user)
	- `description`: string (optional)
	- `color`: string (hex or predefined name, optional)
	- `isDefault`: boolean (optional, default false)
	- `userId`: string (owner)
	- `createdAt`: timestamp
	- `updatedAt`: timestamp

## Endpoints

### 1. Create Category
**POST** `/`
- **Description**: Buat category baru untuk user terautentikasi
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"name": "string",
	"description": "string (optional)",
	"color": "#RRGGBB or name (optional)",
	"isDefault": false
}
```
- **Response**: `201 Created`
```json
{
	"data": {
		"id": "string",
		"name": "string",
		"description": "string|null",
		"color": "string|null",
		"isDefault": false,
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `400 Bad Request` / `409 Conflict` (name already exists)
```json
{
	"error": "Invalid input or category name already exists"
}
```

### 2. Get Categories List
**GET** `/`
- **Description**: Ambil daftar category user terautentikasi
- **Headers**: `Authorization: Bearer {token}`
- **Query Parameters**:
	- `page` (int, default 1)
	- `size` (int, default 50)
	- `sort` (`name`, `createdAt`, prefix `-` for desc)
- **Response**: `200 OK`
```json
{
	"data": [
		{
			"id": "string",
			"name": "string",
			"description": "string|null",
			"color": "string|null",
			"isDefault": false,
			"userId": "string",
			"createdAt": "timestamp",
			"updatedAt": "timestamp"
		}
	],
	"meta": {
		"page": 1,
		"size": 50,
		"totalItems": 10,
		"totalPages": 1
	}
}
```

### 3. Get Single Category
**GET** `/:id`
- **Description**: Ambil detail category berdasarkan ID; hanya owner dapat mengakses
- **Headers**: `Authorization: Bearer {token}`
- **Response**: `200 OK`
```json
{
	"data": {
		"id": "string",
		"name": "string",
		"description": "string|null",
		"color": "string|null",
		"isDefault": false,
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `404 Not Found`
```json
{
	"error": "Category not found"
}
```

### 4. Update Category
**PUT** `/:id`
- **Description**: Perbarui seluruh resource category (owner only)
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"name": "string",
	"description": "string (optional)",
	"color": "string (optional)",
	"isDefault": true|false
}
```
- **Response**: `200 OK`
```json
{
	"data": {
		"id": "string",
		"name": "string",
		"description": "string|null",
		"color": "string|null",
		"isDefault": true|false,
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `400 Bad Request` / `403 Forbidden` / `409 Conflict`

### 5. Patch Category (partial update)
**PATCH** `/:id`
- **Description**: Perbarui sebagian atribut category
- **Headers**: `Authorization: Bearer {token}`
- **Request Body** (contoh):
```json
{
	"description": "Updated description"
}
```
- **Response**: `200 OK`

### 6. Delete Category
**DELETE** `/:id`
- **Description**: Hapus category; only owner. If category contains todos, behavior options:
	- Prevent deletion if todos exist (400/409)
	- Or reassign todos to default category before deletion
	- Or support `force=true` to delete and unassign todos
- **Headers**: `Authorization: Bearer {token}`
- **Query Params**: `force` (boolean, optional)
- **Responses**:
	- `204 No Content` (deleted)
	- `400 Bad Request` / `409 Conflict` (cannot delete non-empty category without `force`)
```json
{
	"error": "Cannot delete category with assigned todos"
}
```

### 7. List Todos By Category
**GET** `/:id/todos`
- **Description**: Ambil todos yang ada di category tertentu (owner-only)
- **Headers**: `Authorization: Bearer {token}`
- **Query Parameters**: supports `page`, `size`, `completed`, `sort`
- **Response**: `200 OK` (same shape as Todos list response)

### 8. Assign / Unassign Todos
**POST** `/:id/assign`
- **Description**: Tambah satu atau beberapa todo ke category
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"todoIds": ["todo1","todo2"]
}
```
- **Response**: `200 OK`

**POST** `/:id/unassign`
- **Description**: Hapus penugasan todo dari category
- **Request Body**:
```json
{
	"todoIds": ["todo1"]
}
```

## Validation & Errors
- Standard codes: `400` validation, `401` auth, `403` forbidden, `404` not found, `409` conflict, `500` server error.
- Error body:
```json
{
	"error": "Error message",
	"details": { "field": "message" }
}
```

## Examples
- Create category (curl):
```
POST /api/v1/categories
Authorization: Bearer {token}
Content-Type: application/json

{
	"name": "Belanja",
	"description": "Kategori untuk belanja harian",
	"color": "#FFAA00"
}
```

## Notes / Recommendations
- Enforce per-user uniqueness of `name`.
- Provide a default category per user to hold unassigned todos.
- When deleting, prefer safe defaults: require `force=true` to delete non-empty categories and document behavior.
- Use UTC ISO-8601 timestamps and consistent validation for `color` values.


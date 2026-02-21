# Todo API Specification

## Base URL
```
/api/v1/todos
```

## Authentication
- Most endpoints require `Authorization: Bearer {token}` header (JWT issued by `/api/v1/users/login`).

## Endpoints

### 1. Create Todo
**POST** `/`
- **Description**: Buat todo baru untuk user yang terautentikasi
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"title": "string",
	"description": "string (optional)",
	"dueDate": "ISO-8601 timestamp (optional)",
	"priority": "low|medium|high (optional)"
}
```
- **Response**: `201 Created`
```json
{
	"data": {
		"id": "string",
		"title": "string",
		"description": "string",
		"completed": false,
		"dueDate": "timestamp|null",
		"priority": "low|medium|high|null",
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `400 Bad Request`
```json
{
	"error": "Invalid input"
}
```

### 2. Get Todo List
**GET** `/`
- **Description**: Ambil daftar todo user terautentikasi, dengan pagination dan filter
- **Headers**: `Authorization: Bearer {token}`
- **Query Parameters**:
	- `page` (int, default 1)
	- `size` (int, default 20)
	- `completed` (true|false) — filter berdasarkan status
	- `dueBefore` (ISO-8601) — todos dengan dueDate sebelum nilai ini
	- `dueAfter` (ISO-8601) — todos dengan dueDate setelah nilai ini
	- `priority` (low|medium|high)
	- `sort` (`createdAt`, `dueDate`, `priority`, prefix `-` for desc)
- **Response**: `200 OK`
```json
{
	"data": [
		{
			"id": "string",
			"title": "string",
			"description": "string",
			"completed": false,
			"dueDate": "timestamp|null",
			"priority": "low|medium|high|null",
			"userId": "string",
			"createdAt": "timestamp",
			"updatedAt": "timestamp"
		}
	],
	"meta": {
		"page": 1,
		"size": 20,
		"totalItems": 123,
		"totalPages": 7
	}
}
```
- **Error Response**: `401 Unauthorized` if token invalid or missing
```json
{
	"error": "Unauthorized"
}
```

### 3. Get Single Todo
**GET** `/:id`
- **Description**: Ambil detail todo berdasarkan ID; hanya owner dapat mengakses
- **Headers**: `Authorization: Bearer {token}`
- **Response**: `200 OK`
```json
{
	"data": {
		"id": "string",
		"title": "string",
		"description": "string",
		"completed": false,
		"dueDate": "timestamp|null",
		"priority": "low|medium|high|null",
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `404 Not Found`
```json
{
	"error": "Todo not found"
}
```

### 4. Update Todo
**PUT** `/:id`
- **Description**: Perbarui seluruh resource todo (owner only)
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"title": "string",
	"description": "string (optional)",
	"completed": true|false,
	"dueDate": "ISO-8601 timestamp (optional)",
	"priority": "low|medium|high (optional)"
}
```
- **Response**: `200 OK`
```json
{
	"data": {
		"id": "string",
		"title": "string",
		"description": "string",
		"completed": true|false,
		"dueDate": "timestamp|null",
		"priority": "low|medium|high|null",
		"userId": "string",
		"createdAt": "timestamp",
		"updatedAt": "timestamp"
	}
}
```
- **Error Response**: `400 Bad Request` / `403 Forbidden`
```json
{
	"error": "Invalid input"
}
```

### 5. Patch Todo (partial update)
**PATCH** `/:id`
- **Description**: Perbarui sebagian atribut todo (mis. toggle `completed`)
- **Headers**: `Authorization: Bearer {token}`
- **Request Body** (contoh untuk toggle):
```json
{
	"completed": true
}
```
- **Response**: `200 OK` (mengembalikan resource yang diperbarui seperti pada PUT)
- **Error Response**: `400 Bad Request` / `403 Forbidden`

### 6. Delete Todo
**DELETE** `/:id`
- **Description**: Hapus todo; hanya owner atau admin dapat menghapus
- **Headers**: `Authorization: Bearer {token}`
- **Response**: `204 No Content`
- **Error Response**: `403 Forbidden`
```json
{
	"error": "Unauthorized to delete this todo"
}
```

### 7. Bulk Operations
**POST** `/bulk-delete`
- **Description**: Hapus beberapa todo sekaligus (owner only)
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
	"ids": ["id1","id2"]
}
```
- **Response**: `200 OK`
```json
{
	"data": {"deleted": 2}
}
```

## Validation & Errors
- Use standard HTTP status codes: `400` validation, `401` auth, `403` forbidden, `404` not found, `500` server error.
- Error response format:
```json
{
	"error": "Error message",
	"details": { "field": "message" } // optional
}
```

## Examples
- Create Todo request (curl):
```
POST /api/v1/todos
Authorization: Bearer {token}
Content-Type: application/json

{
	"title": "Beli bahan makanan",
	"description": "Susu, telur, roti",
	"dueDate": "2026-03-01T10:00:00Z",
	"priority": "medium"
}
```

## Notes
- Each todo is owned by a user (`userId`) — enforce authorization so users can only access their own todos unless admin.
- Support ISO-8601 timestamps and consistent timezone handling (prefer UTC storage).
- Consider soft-delete (flag) if you want recoverability; API above assumes hard delete.


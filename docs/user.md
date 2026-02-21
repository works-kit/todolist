# User API Specification

## Base URL
```
/api/v1/users
```

## Endpoints

### 1. Create User
**POST** `/register`
- **Description**: Register a new user
- **Request Body**:
```json
{
    "name": "string",
    "email": "string",
    "password": "string"
}
```
- **Response**: `201 Created`
```json
{
    "data": {
        "id": "string",
        "name": "string",
        "email": "string"
    }
}
```
- **Error Response**: `400 Bad Request`
```json
{
    "error": "Invalid input or email already exists"
}
```

### 2. Login
**POST** `/login`
- **Request Body**:
```json
{
    "email": "string",
    "password": "string"
}
```
- **Response**: `200 OK`
```json
{
    "data" : {
        "token": "string",
        "user": {"id": "string", "name": "string", "email": "string"}
    }
}
```
- **Error Response**: `401 Unauthorized`
```json
{
    "error": "Invalid email or password"
}
```

### 3. Get User Profile
**GET** `/:id`
- **Headers**: `Authorization: Bearer {token}`
- **Response**: `200 OK`
```json
{
    "data": {
       "id": "string",
        "name": "string",
        "email": "string",
        "createdAt": "timestamp"
    }
}
```
- **Error Response**: `404 Not Found`
```json
{
    "error": "User not found"
}
```

### 4. Update User
**PUT** `/:id`
- **Headers**: `Authorization: Bearer {token}`
- **Request Body**:
```json
{
    "data" : {
        "name": "string",
        "password": "string"
    }
}
```
- **Response**: `200 OK`
- **Error Response**: `400 Bad Request`
```json
{
    "error": "Invalid input"
}
```

### 5. Delete User
**DELETE** `/:id`
- **Headers**: `Authorization: Bearer {token}`
- **Response**: `204 No Content`
- **Error Response**: `403 Forbidden`
```json
{
    "error": "Unauthorized to delete this user"
}
```


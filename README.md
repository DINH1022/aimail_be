1. Đăng ký (Register)
Method: POST
URL: http://localhost:8080/auth/register
Body (raw, JSON):
{
  "email": "user@example.com",
  "password": "password123"
}
Response: JSON chứa accessToken và refreshToken, email.

2. Đăng nhập (Login)
Method: POST
URL: http://localhost:8080/auth/login
Body:
{
  "email": "user@example.com",
  "password": "password123"
}
Response: JSON chứa accessToken và refreshToken.

3. Refresh token
Method: POST
URL: http://localhost:8080/auth/refresh
Body:
{
  "refreshToken": "{{refreshToken}}"
}
Response trả accessToken mới và refreshToken mới.

4. Logout
Method: POST
URL: http://localhost:8080/auth/logout
Body:
{
  "refreshToken": "{{refreshToken}}"
}
Hệ thống sẽ xóa refresh token khỏi DB.

5. Login với Google (mock)
Method: POST
URL: http://localhost:8080/auth/google
Body: (lưu ý mock yêu cầu idToken chứa @)
{
  "idToken": "user@example.com"
}
Response: giống login (access + refresh token)

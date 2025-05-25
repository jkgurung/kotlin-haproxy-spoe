#!/bin/bash

# Test script for WAF Agent
echo "🛡️ Testing WAF Agent"
echo "==================="

BASE_URL="http://localhost"

echo -e "\n1. Testing legitimate request..."
curl -s -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/products?category=electronics"

echo -e "\n2. Testing SQL injection..."
curl -s -H "User-Agent: Mozilla/5.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/search?q='; DROP TABLE users; --"

echo -e "\n3. Testing XSS attack..."
curl -s -H "User-Agent: Chrome/90.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/comment?text=<script>alert('xss')</script>"

echo -e "\n4. Testing suspicious user agent..."
curl -s -H "User-Agent: sqlmap/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/"

echo -e "\n5. Testing directory traversal..."
curl -s -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/files?path=../../../etc/passwd"

echo -e "\n6. Testing vulnerability scanning..."
curl -s -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/wp-admin/"

echo -e "\n7. Testing malicious JSON payload..."
curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "User-Agent: TestClient/1.0" \
     -d '{"user": "admin", "password": "'; DROP TABLE users; --"}' \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/login"

echo -e "\n8. Testing oversized URI..."
LONG_PATH=$(printf "a%.0s" {1..3000})
curl -s -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/search?q=$LONG_PATH"

echo -e "\n9. Testing rate limiting (login endpoint)..."
for i in {1..15}; do
    echo -n "Request $i: "
    curl -s -X POST \
         -H "Content-Type: application/json" \
         -d '{"username": "test", "password": "test"}' \
         -w "%{http_code}\n" \
         "$BASE_URL/login" > /dev/null &
done
wait

echo -e "\n10. Testing XSS in request body..."
curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "User-Agent: TestClient/1.0" \
     -d '{"comment": "<script>document.location=\"http://evil.com\"</script>"}' \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/comments"

echo -e "\nDone!"
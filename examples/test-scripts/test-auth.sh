#!/bin/bash

# Test script for Authorization Agent
echo "🔐 Testing Authorization Agent"
echo "==============================="

BASE_URL="http://localhost"

echo -e "\n1. Testing valid API key..."
curl -s -H "X-API-Key: api-key-12345" \
     -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\nHeaders: %{header_json}\n" \
     "$BASE_URL/api/users"

echo -e "\n2. Testing invalid API key..."
curl -s -H "X-API-Key: invalid-key" \
     -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/api/users"

echo -e "\n3. Testing JWT token..."
curl -s -H "Authorization: Bearer app1.user123.signature" \
     -H "User-Agent: WebApp/2.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/dashboard"

echo -e "\n4. Testing unauthorized admin access..."
curl -s -H "User-Agent: TestClient/1.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/admin/settings"

echo -e "\n5. Testing user trying to access admin..."
curl -s -H "Authorization: Bearer app1.user456.signature" \
     -H "User-Agent: WebApp/2.0" \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/admin/settings"

echo -e "\n6. Testing session with CSRF..."
curl -s -X POST \
     -H "X-Session-ID: abcdef1234567890abcdef1234567890" \
     -H "X-CSRF-Token: csrf_token_12345" \
     -H "Content-Type: application/json" \
     -d '{"data": "test"}' \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/api/update"

echo -e "\n7. Testing API access with limited key..."
curl -s -X POST \
     -H "X-API-Key: api-key-67890" \
     -H "Content-Type: application/json" \
     -d '{"data": "test"}' \
     -w "\nStatus: %{http_code}\n" \
     "$BASE_URL/api/users"

echo -e "\nDone!"
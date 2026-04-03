#!/bin/bash
# ────────────────────────────────────────────────────────────
# 모니터링 EC2 초기 설정 스크립트
#
# 사용: ./setup.sh <ORDER_IP> [PAY_IP] [AUTH_IP] [PRODUCT_IP] [DELIVERY_IP] [INVENTORY_IP] [GATEWAY_IP]
#
# 예시 (모든 서비스):
#   ./setup.sh 10.0.1.50 10.0.1.51 10.0.1.52 10.0.1.53 10.0.1.54 10.0.1.55 10.0.1.56
#
# 예시 (order-service만):
#   ./setup.sh 10.0.1.50
#
# 미지정 서비스는 prometheus.yml에서 자동 제거됨
# ────────────────────────────────────────────────────────────
set -e

ORDER_IP="${1:?사용법: ./setup.sh <order-service-IP> [pay-IP] [auth-IP] [product-IP] [delivery-IP] [inventory-IP] [gateway-IP]}"
PAY_IP="${2:-}"
AUTH_IP="${3:-}"
PRODUCT_IP="${4:-}"
DELIVERY_IP="${5:-}"
INVENTORY_IP="${6:-}"
GATEWAY_IP="${7:-}"

echo "=== [1/5] Docker 설치 ==="
if ! command -v docker &> /dev/null; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
    https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
  sudo usermod -aG docker $USER
  echo "Docker 설치 완료"
else
  echo "Docker 이미 설치됨 — skip"
fi

echo "=== [2/5] k6 설치 ==="
if ! command -v k6 &> /dev/null; then
  sudo gpg -k
  sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
    --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
  echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | \
    sudo tee /etc/apt/sources.list.d/k6.list
  sudo apt-get update -y
  sudo apt-get install -y k6
  echo "k6 설치 완료"
else
  echo "k6 이미 설치됨 — skip"
fi

echo "=== [3/5] prometheus.yml — 서비스 IP 치환 ==="
cp prometheus.yml prometheus.yml.bak

# order-service (필수)
sed -i "s/ORDER_SERVICE_IP_PLACEHOLDER/${ORDER_IP}/g" prometheus.yml
echo "  order-service → ${ORDER_IP}:8081"

# 선택적 서비스 — IP 미지정 시 해당 job 블록 삭제
apply_or_remove() {
  local placeholder="$1"
  local ip="$2"
  local job_name="$3"
  if [ -n "$ip" ]; then
    sed -i "s/${placeholder}/${ip}/g" prometheus.yml
    echo "  ${job_name} → ${ip}"
  else
    # job 블록 전체 제거 (job_name 행부터 다음 빈줄까지)
    sed -i "/job_name: ${job_name}/,/^$/d" prometheus.yml
    echo "  ${job_name} → IP 미지정, prometheus.yml에서 제거"
  fi
}

apply_or_remove "PAY_SERVICE_IP_PLACEHOLDER"       "$PAY_IP"      "pay-service"
apply_or_remove "AUTH_SERVICE_IP_PLACEHOLDER"      "$AUTH_IP"     "auth-service"
apply_or_remove "PRODUCT_SERVICE_IP_PLACEHOLDER"   "$PRODUCT_IP"  "product-service"
apply_or_remove "DELIVERY_SERVICE_IP_PLACEHOLDER"  "$DELIVERY_IP" "delivery-service"
apply_or_remove "INVENTORY_SERVICE_IP_PLACEHOLDER" "$INVENTORY_IP" "inventory-service"
apply_or_remove "APIGATEWAY_SERVICE_IP_PLACEHOLDER" "$GATEWAY_IP"  "apigateway-service"

echo "prometheus.yml 설정 완료"

echo "=== [4/5] docker.sock 권한 설정 (Promtail) ==="
sudo chmod 666 /var/run/docker.sock 2>/dev/null || true

echo "=== [5/5] 모니터링 스택 기동 ==="
docker compose up -d

MONITORING_IP=$(curl -s ifconfig.me)
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  모니터링 스택 기동 완료"
echo "  Grafana:    http://${MONITORING_IP}:3000  (admin/admin)"
echo "  Prometheus: http://${MONITORING_IP}:9090"
echo "  Loki:       http://${MONITORING_IP}:3100"
echo "  Tempo:      http://${MONITORING_IP}:3200"
echo "  Promtail:   http://${MONITORING_IP}:9080"
echo ""
echo "  각 서비스 ECS 태스크 환경변수에 추가:"
echo "    LOKI_URL=http://${MONITORING_IP}:3100/loki/api/v1/push"
echo "    TEMPO_ENDPOINT=http://${MONITORING_IP}:4318/v1/traces"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

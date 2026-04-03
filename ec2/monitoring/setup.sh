#!/bin/bash
# ────────────────────────────────────────────────────────────
# 모니터링 EC2 초기 설정 스크립트
# 사용: ./setup.sh <주문서비스-EC2-IP>
#
# 예시: ./setup.sh 10.0.1.50
# ────────────────────────────────────────────────────────────
set -e

ORDER_SERVICE_IP="${1:?사용법: ./setup.sh <주문서비스-EC2-IP>}"

echo "=== [1/4] Docker 설치 ==="
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

echo "=== [2/4] k6 설치 ==="
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

echo "=== [3/4] Prometheus 설정에 order-service IP 적용 ==="
sed -i "s/ORDER_SERVICE_IP_PLACEHOLDER/${ORDER_SERVICE_IP}/g" prometheus.yml
echo "prometheus.yml → ${ORDER_SERVICE_IP}:8081 으로 설정 완료"

echo "=== [4/4] 모니터링 스택 기동 ==="
docker compose up -d

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  모니터링 스택 기동 완료"
echo "  Grafana:    http://$(curl -s ifconfig.me):3000  (admin/admin)"
echo "  Prometheus: http://$(curl -s ifconfig.me):9090"
echo "  Loki:       http://$(curl -s ifconfig.me):3100"
echo "  Tempo:      http://$(curl -s ifconfig.me):4318"
echo ""
echo "  order-service EC2에 설정할 환경변수:"
MONITORING_IP=$(curl -s ifconfig.me)
echo "    LOKI_URL=http://${MONITORING_IP}:3100"
echo "    TEMPO_ENDPOINT=http://${MONITORING_IP}:4318/v1/traces"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

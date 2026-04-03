#!/bin/bash
# ────────────────────────────────────────────────────────────
# 주문서비스 EC2 초기 설정 스크립트
# 사용: ./setup.sh <모니터링-EC2-IP>
#
# 예시: ./setup.sh 10.0.1.100
#
# 사전 준비:
#   - order-service JAR를 이 EC2에 복사해두기
#     scp build/libs/order-service-*.jar ec2-user@<EC2-IP>:~/
#   - DB 관련 환경변수는 아래 직접 수정
# ────────────────────────────────────────────────────────────
set -e

MONITORING_IP="${1:?사용법: ./setup.sh <모니터링-EC2-IP>}"

echo "=== [1/3] Java 21 설치 ==="
if ! java -version 2>&1 | grep -q "21"; then
  sudo apt-get update -y
  sudo apt-get install -y openjdk-21-jdk
  echo "Java 21 설치 완료"
else
  echo "Java 21 이미 설치됨 — skip"
fi

echo "=== [2/3] Docker 설치 (MySQL용) ==="
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

echo "=== [3/3] MySQL 기동 ==="
docker run -d \
  --name order-mysql \
  --restart unless-stopped \
  -e MYSQL_DATABASE=order \
  -e MYSQL_USER=myuser \
  -e MYSQL_PASSWORD=order111 \
  -e MYSQL_ROOT_PASSWORD=orderroot \
  -p 3307:3306 \
  mysql:8.0 \
  --server-id=1 \
  --log-bin=mysql-bin \
  --binlog-format=ROW \
  --binlog-row-image=FULL || echo "MySQL 이미 실행 중"

echo "MySQL 기동 완료 (30초 대기중...)"
sleep 30

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  설정 완료. 아래 명령으로 order-service 실행:"
echo ""
echo "  export LOKI_URL=http://${MONITORING_IP}:3100"
echo "  export TEMPO_ENDPOINT=http://${MONITORING_IP}:4318/v1/traces"
echo "  export DB_HOST=localhost"
echo "  export DB_PORT=3307"
echo "  export DB_NAME=order"
echo "  export DB_USER=myuser"
echo "  export DB_PASSWORD=order111"
echo ""
echo "  java -jar order-service-*.jar \\"
echo "    --spring.profiles.active=prod,stub \\"
echo "    --spring.datasource.url=jdbc:mysql://localhost:3307/order \\"
echo "    --spring.datasource.username=myuser \\"
echo "    --spring.datasource.password=order111 \\"
echo "    --spring.jpa.hibernate.ddl-auto=update"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

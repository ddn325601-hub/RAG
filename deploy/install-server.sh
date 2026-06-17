#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Please run as root: sudo bash deploy/install-server.sh"
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release software-properties-common unzip tar nginx openjdk-17-jdk maven

if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

systemctl enable --now docker
systemctl enable --now nginx

mkdir -p /opt/super-biz-agent-data

echo "Installed versions:"
java -version
mvn -version | head -n 1
docker --version
docker compose version
nginx -v

echo "Server dependencies installed."
